package com.newamerica.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.newamerica.contracts.RequestContract;
import com.newamerica.states.RequestState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.newamerica.flows.CordappConfigUtilities.getPreferredNotary;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class ApproveRequestFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class InitiatorFlow extends FlowLogic<SignedTransaction> {
        private final UniqueIdentifier requestStateLinearId;

        public InitiatorFlow(UniqueIdentifier requestStateLinearId) {
            this.requestStateLinearId = requestStateLinearId;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            List<UUID> requestStateLinearIdList = new ArrayList<>();
            requestStateLinearIdList.add(requestStateLinearId.getId());

            //get StatAndRef for the respective RequestState
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, requestStateLinearIdList);
            Vault.Page results = getServiceHub().getVaultService().queryBy(RequestState.class, queryCriteria);
            StateAndRef stateRef = (StateAndRef) results.getStates().get(0);
            RequestState inputRequestState = (RequestState) stateRef.getState().getData();

            RequestState outputRequestState = inputRequestState.changeStatus(RequestState.RequestStateStatus.APPROVED);

            final Party notary = getPreferredNotary(getServiceHub());
            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);
            CommandData commandData = new RequestContract.Commands.Approve();
            outputRequestState.getParticipants().add(getOurIdentity());
            transactionBuilder.addCommand(commandData, outputRequestState.getParticipants().stream().map(i -> (i.getOwningKey())).collect(Collectors.toList()));
            transactionBuilder.addOutputState(outputRequestState, RequestContract.ID);
            transactionBuilder.verify(getServiceHub());

            //partially sign transaction
            SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(transactionBuilder, getOurIdentity().getOwningKey());

            //create list of all parties minus ourIdentity for required signatures
            List<Party> otherParties = outputRequestState.getParticipants().stream().map(i -> ((Party) i)).collect(Collectors.toList());
            otherParties.remove(getOurIdentity());

            //create sessions based on otherParties
            List<FlowSession> flowSessions = otherParties.stream().map(i -> initiateFlow(i)).collect(Collectors.toList());

            SignedTransaction signedTransaction = subFlow(new CollectSignaturesFlow(partSignedTx, flowSessions));
            RequestState approveRequestState = (RequestState) subFlow(new FinalityFlow(signedTransaction, flowSessions)).getTx().getOutputStates().get(0);
            return subFlow(new UpdateFundBalanceFlow.InitiatorFlow(
                    approveRequestState
            ));
        }
    }
    /**
     * This is the flow which approves RequestState updates.
     */

    @InitiatedBy(ApproveRequestFlow.InitiatorFlow.class)
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
                        req.using("This must be an RequestState transaction", output instanceof RequestState);
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