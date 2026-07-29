//
// Copyright (C) 2025 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include "nfa_ee_api.cc"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "mock_gki_utils.h"
#include "nfa_ee_int.h"
#include "nfa_hci_int.h"

class NfaEeApiTest : public ::testing::Test {
  void SetUp() override { gki_utils = new MockGkiUtils(); }
  void TearDown() override {
    delete gki_utils;
    gki_utils = nullptr;
  }
};

// Tests for NFA_EeDiscover
TEST_F(NfaEeApiTest, NFA_EeDiscoverInvalidState) {
  tNFA_EE_CBACK* p_ee_cback = nullptr;
  nfa_ee_cb.em_state = NFA_EE_EM_STATE_INIT;
  tNFA_STATUS status = NFA_EeDiscover(p_ee_cback);
  EXPECT_EQ(status, NFA_STATUS_FAILED);
}

TEST_F(NfaEeApiTest, NFA_EeDiscoverInvalidParam) {
  tNFA_EE_CBACK* p_ee_cback = nullptr;
  nfa_ee_cb.em_state = NFA_EE_EM_STATE_INIT_DONE;
  tNFA_STATUS status = NFA_EeDiscover(p_ee_cback);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeDiscoverNormal) {
  tNFA_EE_CBACK* mock_callback = [](tNFA_EE_EVT /*event*/,
                                    tNFA_EE_CBACK_DATA* /*p_data*/) {};
  nfa_ee_cb.p_ee_disc_cback = nullptr;
  nfa_ee_cb.em_state = NFA_EE_EM_STATE_INIT_DONE;
  tNFA_EE_API_DISCOVER* p_msg =
      (tNFA_EE_API_DISCOVER*)malloc(sizeof(tNFA_EE_API_DISCOVER));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils), getbuf(sizeof(tNFA_EE_API_DISCOVER)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeDiscover(mock_callback);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeGetInfo
TEST_F(NfaEeApiTest, NFA_EeGetInfoInvalidParam) {
  tNFA_STATUS status = NFA_EeGetInfo(nullptr, nullptr);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeGetInfoInvalidState) {
  memset(&nfa_hci_cb, 0, sizeof(nfa_hci_cb));
  nfa_hci_cb.num_nfcee = 1;
  nfa_hci_cb.ee_info[0].ee_interface[0] = 0;
  nfa_ee_cb.em_state = NFA_EE_EM_STATE_INIT;
  tNFA_STATUS status = NFA_EeGetInfo(&nfa_hci_cb.num_nfcee, nfa_hci_cb.ee_info);
  EXPECT_EQ(status, NFA_STATUS_FAILED);
}

TEST_F(NfaEeApiTest, NFA_EeGetInfoOneNfcEe) {
  memset(&nfa_hci_cb, 0, sizeof(nfa_hci_cb));
  memset(&nfa_ee_cb, 0, sizeof(nfa_ee_cb));
  nfa_ee_cb.cur_ee = 1;
  nfa_hci_cb.num_nfcee = 2;
  nfa_hci_cb.ee_info[0].ee_interface[0] = 0;
  nfa_ee_cb.em_state = NFA_EE_EM_STATE_INIT_DONE;
  nfa_ee_cb.ecb[0].nfcee_id = 0x10;
  tNFA_STATUS status = NFA_EeGetInfo(&nfa_hci_cb.num_nfcee, nfa_hci_cb.ee_info);
  EXPECT_EQ(status, NFA_STATUS_OK);
  EXPECT_EQ(nfa_hci_cb.num_nfcee, 1);
  EXPECT_EQ(nfa_hci_cb.ee_info[0].ee_handle,
            NFA_HANDLE_GROUP_EE | (tNFA_HANDLE)nfa_ee_cb.ecb[0].nfcee_id);
}

// Tests for NFA_EeRegister
TEST_F(NfaEeApiTest, NFA_EeRegisterInvalidParam) {
  tNFA_STATUS status = NFA_EeRegister(nullptr);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeRegisterNormal) {
  tNFA_EE_CBACK* mock_callback = [](tNFA_EE_EVT /*event*/,
                                    tNFA_EE_CBACK_DATA* /*p_data*/) {};
  tNFA_EE_API_REGISTER* p_msg =
      (tNFA_EE_API_REGISTER*)malloc(sizeof(tNFA_EE_API_REGISTER));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils), getbuf(sizeof(tNFA_EE_API_REGISTER)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeRegister(mock_callback);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeDeregister
TEST_F(NfaEeApiTest, NFA_EeDeregister) {
  memset(&nfa_ee_cb, 0, sizeof(nfa_ee_cb));
  tNFA_EE_CBACK* mock_callback = [](tNFA_EE_EVT /*event*/,
                                    tNFA_EE_CBACK_DATA* /*p_data*/) {};
  nfa_ee_cb.p_ee_cback[0] = mock_callback;
  tNFA_EE_API_DEREGISTER* p_msg =
      (tNFA_EE_API_DEREGISTER*)malloc(sizeof(tNFA_EE_API_DEREGISTER));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf(sizeof(tNFA_EE_API_DEREGISTER)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeDeregister(mock_callback);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeModeSet
TEST_F(NfaEeApiTest, NFA_EeModeSetInvalidNfceeId) {
  memset(&nfa_ee_cb, 0, sizeof(nfa_ee_cb));
  nfa_ee_cb.cur_ee = 2;
  nfa_ee_cb.ecb[0].nfcee_id = 0;
  nfa_ee_cb.ecb[1].nfcee_id = 0x10;

  tNFA_STATUS status = NFA_EeModeSet(0xff, NFA_EE_MD_ACTIVATE);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeModeSetActivate) {
  memset(&nfa_ee_cb, 0, sizeof(nfa_ee_cb));
  nfa_ee_cb.cur_ee = 2;
  nfa_ee_cb.ecb[0].nfcee_id = 0;
  nfa_ee_cb.ecb[1].nfcee_id = 0x10;

  tNFA_EE_API_MODE_SET* p_msg =
      (tNFA_EE_API_MODE_SET*)malloc(sizeof(tNFA_EE_API_MODE_SET));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils), getbuf(sizeof(tNFA_EE_API_MODE_SET)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeModeSet(0x10, NFA_EE_MD_ACTIVATE);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeSetDefaultTechRouting
TEST_F(NfaEeApiTest, NFA_EeSetDefaultTechRoutingInvalidNfceeId) {
  tNFA_STATUS status =
      NFA_EeSetDefaultTechRouting(0xff, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeSetDefaultTechRoutingHost) {
  tNFA_EE_API_SET_TECH_CFG* p_msg =
      (tNFA_EE_API_SET_TECH_CFG*)malloc(sizeof(tNFA_EE_API_SET_TECH_CFG));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf(sizeof(tNFA_EE_API_SET_TECH_CFG)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status =
      NFA_EeSetDefaultTechRouting(NFC_DH_ID, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeClearDefaultTechRouting
TEST_F(NfaEeApiTest, NFA_EeClearDefaultTechRoutingInvalidNfceeId) {
  tNFA_STATUS status = NFA_EeClearDefaultTechRouting(0xff, 0x1);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeClearDefaultTechRoutingHost) {
  tNFA_EE_API_CLEAR_TECH_CFG* p_msg =
      (tNFA_EE_API_CLEAR_TECH_CFG*)malloc(sizeof(tNFA_EE_API_CLEAR_TECH_CFG));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf(sizeof(tNFA_EE_API_CLEAR_TECH_CFG)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeClearDefaultTechRouting(NFC_DH_ID, 0x1);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeSetDefaultProtoRouting
TEST_F(NfaEeApiTest, NFA_EeSetDefaultProtoRoutingInvalidNfceeId) {
  tNFA_STATUS status =
      NFA_EeSetDefaultProtoRouting(0xff, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeSetDefaultProtoRoutingHost) {
  tNFA_EE_API_SET_PROTO_CFG* p_msg =
      (tNFA_EE_API_SET_PROTO_CFG*)malloc(sizeof(tNFA_EE_API_SET_PROTO_CFG));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf(sizeof(tNFA_EE_API_SET_PROTO_CFG)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status =
      NFA_EeSetDefaultProtoRouting(NFC_DH_ID, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeClearDefaultProtoRouting
TEST_F(NfaEeApiTest, NFA_EeClearDefaultProtoRoutingInvalidNfceeId) {
  tNFA_STATUS status = NFA_EeClearDefaultProtoRouting(0xff, 0x1);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeClearDefaultProtoRoutingClearNone) {
  tNFA_STATUS status = NFA_EeClearDefaultProtoRouting(0xff, 0x0);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

TEST_F(NfaEeApiTest, NFA_EeClearDefaultProtoRoutingHost) {
  tNFA_EE_API_SET_PROTO_CFG* p_msg =
      (tNFA_EE_API_SET_PROTO_CFG*)malloc(sizeof(tNFA_EE_API_SET_PROTO_CFG));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf(sizeof(tNFA_EE_API_SET_PROTO_CFG)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeClearDefaultProtoRouting(NFC_DH_ID, 0x1);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeAddAidRouting
TEST_F(NfaEeApiTest, NFA_EeAddAidRoutingInvalidNfceeId) {
  uint8_t aid[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
  tNFA_STATUS status = NFA_EeAddAidRouting(0xff, sizeof(aid), aid, 0x1, 0x1);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeAddAidRoutingInvalidAid) {
  uint8_t aid[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
  tNFA_STATUS status = NFA_EeAddAidRouting(NFC_DH_ID, 0, nullptr, 0x1, 0x1);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeAddAidRoutingRegisterOneAidToHost) {
  uint8_t aid[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
  uint8_t size = sizeof(tNFA_EE_API_ADD_AID) + sizeof(aid);
  tNFA_EE_API_ADD_AID* p_msg = (tNFA_EE_API_ADD_AID*)malloc(size);

  EXPECT_CALL(*((MockGkiUtils*)gki_utils), getbuf(size))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status =
      NFA_EeAddAidRouting(NFC_DH_ID, sizeof(aid), aid, 0x1, 0x1);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeRemoveAidRouting
TEST_F(NfaEeApiTest, NFA_EeRemoveAidRoutingInvalidAid1) {
  uint8_t aid[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
  tNFA_STATUS status = NFA_EeRemoveAidRouting(sizeof(aid), nullptr);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeRemoveAidRoutingInvalidAid2) {
  uint8_t aid[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
  tNFA_STATUS status = NFA_EeRemoveAidRouting(NFA_MAX_AID_LEN + 1, aid);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeRemoveAidRoutingValidAid) {
  uint8_t aid[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
  uint8_t size = sizeof(tNFA_EE_API_REMOVE_AID) + sizeof(aid);
  tNFA_EE_API_REMOVE_AID* p_msg = (tNFA_EE_API_REMOVE_AID*)malloc(size);

  EXPECT_CALL(*((MockGkiUtils*)gki_utils), getbuf(size))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeRemoveAidRouting(sizeof(aid), aid);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeAddSystemCodeRouting
TEST_F(NfaEeApiTest, NFA_EeAddSystemCodeRoutingInvalidNfceeId) {
  tNFA_STATUS status = NFA_EeAddSystemCodeRouting(0xFEFE, 0xff, 0x1);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeAddSystemCodeRoutingInvalidSystemCode) {
  tNFA_STATUS status = NFA_EeAddSystemCodeRouting(0x0, NFC_DH_ID, 0x1);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeAddSystemCodeRoutingNotSupport) {
  nfc_cb.nci_version = NCI_VERSION_1_0;
  nfc_cb.isScbrSupported = false;
  tNFA_STATUS status = NFA_EeAddSystemCodeRouting(0xFEFE, NFC_DH_ID, 0x1);
  EXPECT_EQ(status, NFA_STATUS_NOT_SUPPORTED);
}

TEST_F(NfaEeApiTest,
       NFA_EeAddSystemCodeRoutingRegisterDefaultSystemCodeToHost) {
  nfc_cb.nci_version = NCI_VERSION_2_0;
  tNFA_EE_API_ADD_SYSCODE* p_msg =
      (tNFA_EE_API_ADD_SYSCODE*)malloc(sizeof(tNFA_EE_API_ADD_SYSCODE));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf(sizeof(tNFA_EE_API_ADD_SYSCODE)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeAddSystemCodeRouting(0xFEFE, NFC_DH_ID, 0x1);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeRemoveSystemCodeRouting
TEST_F(NfaEeApiTest, NFA_EeRemoveSystemCodeRoutingInvalidSystemCode) {
  tNFA_STATUS status = NFA_EeRemoveSystemCodeRouting(0x0);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeRemoveSystemCodeRoutingNotSupport) {
  nfc_cb.nci_version = NCI_VERSION_1_0;
  nfc_cb.isScbrSupported = false;
  tNFA_STATUS status = NFA_EeRemoveSystemCodeRouting(0xFEFE);
  EXPECT_EQ(status, NFA_STATUS_NOT_SUPPORTED);
}

TEST_F(NfaEeApiTest,
       NFA_EeRemoveSystemCodeRoutingRegisterDefaultSystemCodeToHost) {
  nfc_cb.nci_version = NCI_VERSION_2_0;
  tNFA_EE_API_REMOVE_SYSCODE* p_msg =
      (tNFA_EE_API_REMOVE_SYSCODE*)malloc(sizeof(tNFA_EE_API_REMOVE_SYSCODE));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf(sizeof(tNFA_EE_API_REMOVE_SYSCODE)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeRemoveSystemCodeRouting(0xFEFE);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_GetAidTableSize
TEST_F(NfaEeApiTest, GetAidTableSize) { ASSERT_GE(NFA_GetAidTableSize(), 0); }

// Tests for NFA_EeGetLmrtRemainingSize
TEST_F(NfaEeApiTest, NFA_EeGetLmrtRemainingSize) {
  tNFA_EE_API_LMRT_SIZE* p_msg =
      (tNFA_EE_API_LMRT_SIZE*)malloc(sizeof(tNFA_EE_API_LMRT_SIZE));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf(sizeof(tNFA_EE_API_LMRT_SIZE)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeGetLmrtRemainingSize();

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeUpdateNow
TEST_F(NfaEeApiTest, NFA_EeUpdateNowInProgress) {
  nfa_ee_cb.ee_wait_evt |= NFA_EE_WAIT_UPDATE_ALL;

  tNFA_STATUS status = NFA_EeUpdateNow();
  EXPECT_EQ(status, NFA_STATUS_SEMANTIC_ERROR);

  nfa_ee_cb.ee_wait_evt &= ~NFA_EE_WAIT_UPDATE_ALL;
}

TEST_F(NfaEeApiTest, NFA_EeUpdateNowNormal) {
  NFC_HDR* p_msg = (NFC_HDR*)malloc(NFC_HDR_SIZE);

  EXPECT_CALL(*((MockGkiUtils*)gki_utils), getbuf(NFC_HDR_SIZE))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeUpdateNow();

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeConnect
TEST_F(NfaEeApiTest, NFA_EeConnectInvalidEe) {
  tNFA_EE_CBACK* mock_callback = [](tNFA_EE_EVT /*event*/,
                                    tNFA_EE_CBACK_DATA* /*p_data*/) {};
  tNFA_STATUS status = NFA_EeConnect(0xFF, 0x0, mock_callback);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeConnectNullCback) {
  tNFA_STATUS status = NFA_EeConnect(NFC_DH_ID, 0x0, nullptr);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeConnectHost) {
  tNFA_EE_CBACK* mock_callback = [](tNFA_EE_EVT /*event*/,
                                    tNFA_EE_CBACK_DATA* /*p_data*/) {};
  tNFA_EE_API_CONNECT* p_msg =
      (tNFA_EE_API_CONNECT*)malloc(sizeof(tNFA_EE_API_CONNECT));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils), getbuf(sizeof(tNFA_EE_API_CONNECT)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeConnect(NFC_DH_ID, 0x0, mock_callback);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeSendData
TEST_F(NfaEeApiTest, NFA_EeSendDataInvalidEe) {
  uint8_t data[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

  tNFA_STATUS status = NFA_EeSendData(0xff, sizeof(data), data);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeSendDataInvalidNullData) {
  uint8_t data[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

  tNFA_STATUS status = NFA_EeSendData(NFC_DH_ID, sizeof(data), nullptr);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeSendDataToHost) {
  memset(&nfa_ee_cb, 0, sizeof(nfa_ee_cb));
  nfa_ee_cb.ecb[NFA_EE_CB_4_DH].conn_st = NFA_EE_CONN_ST_CONN;
  uint8_t data[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
  uint8_t data_len = sizeof(data);
  tNFA_EE_API_SEND_DATA* p_msg = (tNFA_EE_API_SEND_DATA*)malloc(
      (uint16_t)(sizeof(tNFA_EE_API_SEND_DATA) + data_len));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf((uint16_t)(sizeof(tNFA_EE_API_SEND_DATA) + data_len)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeSendData(NFC_DH_ID, sizeof(data), data);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeDisconnect
TEST_F(NfaEeApiTest, NFA_EeDisconnectInvalidEe) {
  tNFA_STATUS status = NFA_EeDisconnect(0xff);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EeDisconnectHost) {
  memset(&nfa_ee_cb, 0, sizeof(nfa_ee_cb));
  nfa_ee_cb.ecb[NFA_EE_CB_4_DH].conn_st = NFA_EE_CONN_ST_CONN;
  tNFA_EE_API_DISCONNECT* p_msg =
      (tNFA_EE_API_DISCONNECT*)malloc(sizeof(tNFA_EE_API_DISCONNECT));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf(sizeof(tNFA_EE_API_DISCONNECT)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeDisconnect(NFC_DH_ID);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EePowerAndLinkCtrl
TEST_F(NfaEeApiTest, NFA_EePowerAndLinkCtrlInvalidEe) {
  tNFA_STATUS status = NFA_EePowerAndLinkCtrl(0xff, 0x01);
  EXPECT_EQ(status, NFA_STATUS_INVALID_PARAM);
}

TEST_F(NfaEeApiTest, NFA_EePowerAndLinkCtrlHost) {
  memset(&nfa_ee_cb, 0, sizeof(nfa_ee_cb));
  nfa_ee_cb.ecb[NFA_EE_CB_4_DH].ee_status = NFA_EE_STATUS_ACTIVE;
  tNFA_EE_API_PWR_AND_LINK_CTRL* p_msg = (tNFA_EE_API_PWR_AND_LINK_CTRL*)malloc(
      sizeof(tNFA_EE_API_PWR_AND_LINK_CTRL));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf(sizeof(tNFA_EE_API_PWR_AND_LINK_CTRL)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EePowerAndLinkCtrl(NFC_DH_ID, 0x01);

  free(p_msg);
  EXPECT_EQ(status, NFA_STATUS_OK);
}

// Tests for NFA_EeClearRoutingTable
TEST_F(NfaEeApiTest, NFA_EeClearRoutingTable) {
  tNFA_EE_API_CLEAR_ROUTING_TABLE* p_msg =
      (tNFA_EE_API_CLEAR_ROUTING_TABLE*)malloc(
          sizeof(tNFA_EE_API_CLEAR_ROUTING_TABLE));

  EXPECT_CALL(*((MockGkiUtils*)gki_utils),
              getbuf(sizeof(tNFA_EE_API_CLEAR_ROUTING_TABLE)))
      .WillOnce(testing::Return(p_msg));
  tNFA_STATUS status = NFA_EeClearRoutingTable(true, true, true);
  EXPECT_EQ(status, NFA_STATUS_OK);
}
