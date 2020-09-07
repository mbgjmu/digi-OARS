import React, { useState } from "react";
import * as Constants from '../../../constants';
import {
  CCard,
  CCardHeader,
  CBadge,
  CButton,
  CCardBody,
  CDataTable,
  CCollapse,
  CCol,
  CRow,
  CProgress,
  CModal,
  CModalHeader,
  CModalTitle,
  CModalBody
} from "@coreui/react";
import Moment from "moment";
import { FundData } from "../../../data/Funds";
import { RequestForm } from "../withdrawals/RequestForm";

export const AvailableFundsTable = () => {
  const fundData = FundData;
  const [details, setDetails] = useState([]);

  const [show, setShow] = useState(false);

  const handleShow = (item) => setShow(true)
  const handleClose = () => setShow(false);

  const onFormSubmit = (e) => {
    handleClose();
  };

  const toggleDetails = (index) => {
    const position = details.indexOf(index);
    let newDetails = details.slice();
    if (position !== -1) {
      newDetails.splice(position, 1);
    } else {
      newDetails = [...details, index];
    }
    setDetails(newDetails);
  };

  const fields = [
    { key: "originParty", label: "Origin Country" },
    { key: "balance", label: "Balance Available" },
    { key: "datetime", label: "Date" },
    { key: "maxWithdrawalAmount" },
    { key: "status", _style: { width: "20%" } },
    {
      key: "show_details",
      label: "",
      _style: { width: "1%" },
      sorter: false,
      filter: false,
    },
  ];

  const getStatusBadge = (status) => {
    switch (status) {
      case "RECEIVED":
        return "success";
      case "ISSUED":
        return "warning";
      default:
        return "primary";
    }
  };

  const toCurrency = (number, currency) => {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: currency,
    }).format(number);
  };

  return (
    <>
    <CDataTable
      items={fundData.filter(fund => (fund.isReceived === true && fund.balance > 0))}
      fields={fields}
      columnFilter
      tableFilter
      footer
      itemsPerPageSelect
      itemsPerPage={5}
      hover
      sorter
      pagination
      scopedSlots={{
        amount: (item) => <td>{toCurrency(item.amount, item.currency)}</td>,
        balance: (item) => <td>{toCurrency(item.balance, item.currency)}</td>,
        maxWithdrawalAmount: (item) => (
          <td>{toCurrency(item.maxWithdrawalAmount, item.currency)}</td>
        ),
        datetime: (item) => (
          <td>{Moment(item.datetime).format(Constants.DATEFORMAT)}</td>
        ),
        status: (item) => (
          <td>
            <CBadge color={getStatusBadge(item.status)}>{item.status}</CBadge>
          </td>
        ),
        show_details: (item, index) => {
          return (
            <td>
              <CButton
                color="primary"
                variant="outline"
                shape="square"
                size="sm"
                onClick={() => {
                  toggleDetails(index);
                }}
              >
                {details.includes(index) ? "Hide" : "Show"}
              </CButton>
            </td>
          );
        },
        details: (item, index) => {
          return (
            <CCollapse show={details.includes(index)}>
              <CCard className="m-3">
                <CCardHeader>
                  Fund Details
                  <div className="card-header-actions">
                    <CButton
                      className={"float-right mb-0"}
                      color="success"
                      variant="outline"
                      shape="square"
                      size="sm"
                      active={true}
                      onClick={handleShow}
                    >
                      Request Withdrawal
                    </CButton>
                  </div>
                </CCardHeader>
                <CCardBody>
                  {item.isReceived ? (
                    <CRow className="mb-3">
                      <CCol>
                        <p className="text-muted">Total Assets Repatriated:</p>
                        <CProgress
                          value={(item.balance / item.amount) * 100}
                          showPercentage
                          striped
                          color="success"
                          precision={2}
                        />
                      </CCol>
                    </CRow>
                  ) : null}
                  <CRow>
                    <CCol md="3">
                      ID:
                      <br />
                      Origin Country:
                      <br />
                      Receiving Country:
                      <br />
                      Amount:
                      <br />
                      Balance:
                      <br />
                      Max Withdrawal Amount
                    </CCol>
                    <CCol md="3">
                      {item.linearId}
                      <br />
                      {item.originParty}
                      <br />
                      {item.receivingParty}
                      <br />
                      {toCurrency(item.amount, item.currency)}
                      <br />
                      {toCurrency(item.balance, item.currency)}
                      <br />
                      {toCurrency(item.maxWithdrawalAmount, item.currency)}
                    </CCol>
                  </CRow>
                </CCardBody>
              </CCard>
            </CCollapse>
          );
        },
      }}
    />
    <CModal show={show} onClose={handleClose} size="lg">
        <CModalHeader closeButton>
          <CModalTitle>Withdrawal Request Form</CModalTitle>
        </CModalHeader>
        <CModalBody>
          <RequestForm onSubmit={onFormSubmit}/>
        </CModalBody>
      </CModal>
    </>
  );
};