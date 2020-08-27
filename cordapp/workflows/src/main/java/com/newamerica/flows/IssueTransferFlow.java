package com.newamerica.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.newamerica.contracts.TransferContract;
import com.newamerica.states.FundState;
import com.newamerica.states.RequestState;
import com.newamerica.states.TransferState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.newamerica.flows.CordappConfigUtilities.getPreferredNotary;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class IssueTransferFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class InitiatorFlow extends FlowLogic<SignedTransaction> {
        private TransferState outputTransferState;
        private Party issuanceParty;
        private UniqueIdentifier requestStateLinearId;
        private List<AbstractParty> participants;

        public InitiatorFlow (Party issuanceParty, UniqueIdentifier requestStateLinearId, List<AbstractParty> participants) {
            this.issuanceParty = issuanceParty;
            this.requestStateLinearId = requestStateLinearId;
            this.participants = participants;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            List<UUID> requestStateLinearUUID = new ArrayList<>();
            requestStateLinearUUID.add(outputTransferState.getRequestStateLinearId().getId());

            //get reference request state for transfer
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, requestStateLinearUUID);
            Vault.Page results = getServiceHub().getVaultService().queryBy(FundState.class, queryCriteria);
            StateAndRef requestStateRef = (StateAndRef) results.getStates().get(0);
            RequestState requestState = (RequestState) requestStateRef.getState().getData();

            final Party notary = getPreferredNotary(getServiceHub());
            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);
            CommandData commandData = new TransferContract.Commands.Issue();
            transactionBuilder.addCommand(commandData, getOurIdentity().getOwningKey());

            outputTransferState = new TransferState(
                    issuanceParty,
                    requestState.getAuthorizedUserDept(),
                    requestState.getAuthorizedUserUsername(),
                    requestState.getExternalAccountId(),
                    requestState.getAmount(),
                    requestState.getCurrency(),
                    ZonedDateTime.now(),
                    requestStateLinearId,
                    participants
            );

            transactionBuilder.addOutputState(outputTransferState, TransferContract.ID);
            transactionBuilder.verify(getServiceHub());

            //partially sign transaction
            SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(transactionBuilder, getOurIdentity().getOwningKey());

            List<Party> otherParties = outputTransferState.getParticipants().stream().map(i -> ((Party) i)).collect(Collectors.toList());
            otherParties.remove(getOurIdentity());

            //create sessions based on otherParties
            List<FlowSession> flowSessions = otherParties.stream().map(i -> initiateFlow(i)).collect(Collectors.toList());

            SignedTransaction signedTransaction = subFlow(new CollectSignaturesFlow(partSignedTx, flowSessions));
            return subFlow(new FinalityFlow(signedTransaction, flowSessions));
        }
    }

    /**
     * This is the flow which signs TransferState issuances.
     */

    @InitiatedBy(IssueTransferFlow.InitiatorFlow.class)
    public static class ResponderFlow extends FlowLogic<SignedTransaction>{
        private final FlowSession flowSession;
        private SecureHash txWeJustSigned;

        public ResponderFlow(FlowSession flowSession){
            this.flowSession = flowSession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow{

                private SignTxFlow(FlowSession flowSession, ProgressTracker progressTracker){
                    super(flowSession, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx){
                    requireThat(req -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        req.using("This must be an TransferState transaction", output instanceof TransferState);
                        return null;
                    });
                    txWeJustSigned = stx.getId();
                }
            }
            flowSession.getCounterpartyFlowInfo().getFlowVersion();

            // Create a sign transaction flow
            SignTxFlow signTxFlow = new SignTxFlow(flowSession, SignTransactionFlow.Companion.tracker());

            // Run the sign transaction flow to sign the transaction
            subFlow(signTxFlow);
            return subFlow(new ReceiveFinalityFlow(flowSession, txWeJustSigned));
        }
    }
}