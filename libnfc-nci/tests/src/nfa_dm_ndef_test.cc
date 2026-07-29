//
// Copyright (C) 2024 The Android Open Source Project
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
#include "nfa_dm_ndef.cc"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstring>

#include "mock_gki_utils.h"
class MockNDEFHandler {
public:
    MOCK_METHOD(void, OnNDEFData, (uint8_t event, tNFA_NDEF_EVT_DATA* data));
    uint8_t flags;
};
static void NDEFCallbackBridge(uint8_t event, tNFA_NDEF_EVT_DATA* data) {
    extern MockNDEFHandler* g_mock_handler;
    if (g_mock_handler) {
        g_mock_handler->OnNDEFData(event, data);
    }
}
MockNDEFHandler* g_mock_handler = nullptr;
class NfaDmTest : public ::testing::Test {
protected:
    tNFA_DM_CB nfa_dm_cb_mock;
    MockNDEFHandler mock_handler;
    MockNDEFHandler mock_handler1;
    MockNDEFHandler mock_handler2;
    void SetUp() override {
        memset(&nfa_dm_cb_mock, 0, sizeof(nfa_dm_cb_mock));
        g_mock_handler = &mock_handler;
        nfa_dm_cb_mock.p_ndef_handler[0] = reinterpret_cast<
                tNFA_DM_API_REG_NDEF_HDLR*>(&mock_handler1);
        nfa_dm_cb_mock.p_ndef_handler[1] = reinterpret_cast<
                tNFA_DM_API_REG_NDEF_HDLR*>(&mock_handler2);
        gki_utils = new MockGkiUtils();
    }
    void TearDown() override {
        g_mock_handler = nullptr;
        testing::Mock::VerifyAndClearExpectations(&mock_handler);
        gki_utils = nullptr;
    }
};

// nfa_dm_ndef_reg_hdlr

TEST_F(NfaDmTest, RegisterHandler_Success) {
    tNFA_DM_API_REG_NDEF_HDLR reg_info = {};
    reg_info.p_ndef_cback = NDEFCallbackBridge;
    reg_info.tnf = NFA_TNF_DEFAULT;
    reg_info.name_len = 1;
    EXPECT_CALL(mock_handler, OnNDEFData('\0', ::testing::_)).Times(1);
    uint8_t name[] = {'U'};
    memcpy(reg_info.name, name, reg_info.name_len);
    bool result = nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info);
    EXPECT_FALSE(result);
}

TEST_F(NfaDmTest, RegisterHandler_ReplaceExisting) {
    tNFA_DM_API_REG_NDEF_HDLR reg_info1 = {};
    reg_info1.p_ndef_cback = NDEFCallbackBridge;
    reg_info1.tnf = NFA_TNF_DEFAULT;
    reg_info1.name_len = 1;
    uint8_t name1[] = {'1'};
    memcpy(reg_info1.name, name1, reg_info1.name_len);
    EXPECT_CALL(mock_handler, OnNDEFData(::testing::_, ::testing::_)).Times(1);
    nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info1);
    tNFA_DM_API_REG_NDEF_HDLR reg_info2 = {};
    reg_info2.p_ndef_cback = NDEFCallbackBridge;
    reg_info2.tnf = NFA_TNF_DEFAULT;
    reg_info2.name_len = 1;
    EXPECT_CALL(mock_handler, OnNDEFData(::testing::_, ::testing::_)).Times(1);
    uint8_t name2[] = {'2'};
    memcpy(reg_info2.name, name2, reg_info2.name_len);
    bool result = nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info2);
    EXPECT_FALSE(result);
}

//nfa_dm_ndef_dereg_hdlr

TEST_F(NfaDmTest, DeregisterHandler_Success) {
    tNFA_DM_API_REG_NDEF_HDLR reg_info = {};
    reg_info.p_ndef_cback = NDEFCallbackBridge;
    reg_info.tnf = NFA_TNF_DEFAULT;
    reg_info.name_len = 1;
    uint8_t name[] = {'U'};
    memcpy(reg_info.name, name, reg_info.name_len);
    nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info);
    EXPECT_CALL(mock_handler, OnNDEFData(::testing::_, ::testing::_)).Times(0);
    bool result = nfa_dm_ndef_dereg_hdlr((tNFA_DM_MSG*)&reg_info);
    std::cout << "Deregistration result: " << (result ? "Success" : "Failure") << std::endl;
    EXPECT_TRUE(result);
}

TEST_F(NfaDmTest, DeregisterHandler_Fail_HandlerNotRegistered) {
    tNFA_DM_API_REG_NDEF_HDLR reg_info = {};
    reg_info.p_ndef_cback = NDEFCallbackBridge;
    reg_info.tnf = NFA_TNF_DEFAULT;
    reg_info.name_len = 1;
    uint8_t name[] = {'U'};
    memcpy(reg_info.name, name, reg_info.name_len);
    bool result = nfa_dm_ndef_dereg_hdlr((tNFA_DM_MSG*)&reg_info);
    EXPECT_TRUE(result);
}

TEST_F(NfaDmTest, DeregisterHandler_ReleaseSlot) {
    tNFA_DM_API_REG_NDEF_HDLR reg_info1 = {};
    reg_info1.p_ndef_cback = NDEFCallbackBridge;
    reg_info1.tnf = NFA_TNF_DEFAULT;
    reg_info1.name_len = 1;
    uint8_t name1[] = {'1'};
    memcpy(reg_info1.name, name1, reg_info1.name_len);
    nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info1);
    bool result1 = nfa_dm_ndef_dereg_hdlr((tNFA_DM_MSG*)&reg_info1);
    EXPECT_TRUE(result1);
    tNFA_DM_API_REG_NDEF_HDLR reg_info2 = {};
    reg_info2.p_ndef_cback = NDEFCallbackBridge;
    reg_info2.tnf = NFA_TNF_DEFAULT;
    reg_info2.name_len = 1;
    uint8_t name2[] = {'2'};
    memcpy(reg_info2.name, name2, reg_info2.name_len);
    bool result2 = nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info2);
    EXPECT_FALSE(result2);
}
TEST_F(NfaDmTest, DeregisterHandler_AllSlotsOccupied) {
    for (int i = 0; i < NFA_NDEF_MAX_HANDLERS; ++i) {
        tNFA_DM_API_REG_NDEF_HDLR reg_info = {};
        reg_info.p_ndef_cback = NDEFCallbackBridge;
        reg_info.tnf = NFA_TNF_DEFAULT;
        reg_info.name_len = 1;
        uint8_t name[] = {'U'};
        memcpy(reg_info.name, name, reg_info.name_len);
        nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info);
    }
    tNFA_DM_API_REG_NDEF_HDLR reg_info_to_deregister = {};
    reg_info_to_deregister.p_ndef_cback = NDEFCallbackBridge;
    reg_info_to_deregister.tnf = NFA_TNF_DEFAULT;
    reg_info_to_deregister.name_len = 1;
    uint8_t name_to_deregister[] = {'U'};
    memcpy(reg_info_to_deregister.name, name_to_deregister, reg_info_to_deregister.name_len);
    bool result = nfa_dm_ndef_dereg_hdlr((tNFA_DM_MSG*)&reg_info_to_deregister);
    EXPECT_TRUE(result);
    tNFA_DM_API_REG_NDEF_HDLR reg_info_new = {};
    reg_info_new.p_ndef_cback = NDEFCallbackBridge;
    reg_info_new.tnf = NFA_TNF_DEFAULT;
    reg_info_new.name_len = 1;
    uint8_t name_new[] = {'1'};
    memcpy(reg_info_new.name, name_new, reg_info_new.name_len);
    bool result_new = nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info_new);
    EXPECT_FALSE(result_new);
}

//nfa_dm_ndef_handle_message

TEST_F(NfaDmTest, HandleMessage_RegisteredHandler) {
    tNFA_DM_API_REG_NDEF_HDLR reg_info = {};
    reg_info.p_ndef_cback = NDEFCallbackBridge;
    reg_info.tnf = NFA_TNF_DEFAULT;
    reg_info.name_len = 1;
    uint8_t name[] = {'U'};
    memcpy(reg_info.name, name, reg_info.name_len);
    nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info);
    uint8_t event = 1;
    tNFA_NDEF_EVT_DATA event_data = {};
    EXPECT_CALL(mock_handler, OnNDEFData(::testing::_, ::testing::_)).Times(0);
    uint8_t msg_buf[sizeof(tNFA_NDEF_EVT_DATA)];
    memcpy(msg_buf, &event_data, sizeof(tNFA_NDEF_EVT_DATA));
    uint32_t len = sizeof(tNFA_NDEF_EVT_DATA);
    tNFA_STATUS status = NFA_STATUS_OK;
    nfa_dm_ndef_handle_message(status, msg_buf, len);
}

TEST_F(NfaDmTest, HandleMessage_UnregisteredHandler) {
  nfa_dm_cb = nfa_dm_cb_mock;
  uint8_t event = 1;
  tNFA_NDEF_EVT_DATA event_data = {};
  uint8_t msg_buf[sizeof(tNFA_NDEF_EVT_DATA)];
  memcpy(msg_buf, &event_data, sizeof(tNFA_NDEF_EVT_DATA));
  EXPECT_CALL(mock_handler, OnNDEFData(event, &event_data)).Times(0);
  uint32_t len = sizeof(tNFA_NDEF_EVT_DATA);
  tNFA_STATUS status = NFA_STATUS_OK;
  nfa_dm_ndef_handle_message(status, msg_buf, len);
}

TEST_F(NfaDmTest, HandleMessage_InvalidEvent) {
  nfa_dm_cb = nfa_dm_cb_mock;
  tNFA_DM_API_REG_NDEF_HDLR reg_info = {};
  reg_info.p_ndef_cback = NDEFCallbackBridge;
  reg_info.tnf = NFA_TNF_DEFAULT;
  reg_info.name_len = 1;
  uint8_t name[] = {'U'};
  memcpy(reg_info.name, name, reg_info.name_len);
  nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info);
  uint8_t invalid_event = 99;
  tNFA_NDEF_EVT_DATA event_data = {};
  uint8_t msg_buf[sizeof(tNFA_NDEF_EVT_DATA)];
  memcpy(msg_buf, &event_data, sizeof(tNFA_NDEF_EVT_DATA));
  EXPECT_CALL(mock_handler, OnNDEFData(invalid_event, &event_data)).Times(0);
  uint32_t len = sizeof(tNFA_NDEF_EVT_DATA);
  tNFA_STATUS status = NFA_STATUS_OK;
  nfa_dm_ndef_handle_message(status, msg_buf, len);
}

TEST_F(NfaDmTest, HandleMessage_CallbackInvocation) {
  nfa_dm_cb = nfa_dm_cb_mock;
  tNFA_DM_API_REG_NDEF_HDLR reg_info = {};
  reg_info.p_ndef_cback = NDEFCallbackBridge;
  reg_info.tnf = NFA_TNF_DEFAULT;
  reg_info.name_len = 1;
  uint8_t name[] = {'U'};
  memcpy(reg_info.name, name, reg_info.name_len);
  nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info);
  uint8_t event = 2;
  tNFA_NDEF_EVT_DATA event_data = {};
  EXPECT_CALL(mock_handler, OnNDEFData(::testing::_, ::testing::_)).Times(0);
  uint8_t msg_buf[sizeof(tNFA_NDEF_EVT_DATA)];
  memcpy(msg_buf, &event_data, sizeof(tNFA_NDEF_EVT_DATA));
  uint32_t len = sizeof(tNFA_NDEF_EVT_DATA);
  tNFA_STATUS status = NFA_STATUS_OK;
  nfa_dm_ndef_handle_message(status, msg_buf, len);
}

TEST_F(NfaDmTest, HandleMultipleMessages) {
  nfa_dm_cb = nfa_dm_cb_mock;
  tNFA_DM_API_REG_NDEF_HDLR reg_info = {};
  reg_info.p_ndef_cback = NDEFCallbackBridge;
  reg_info.tnf = NFA_TNF_DEFAULT;
  reg_info.name_len = 1;
  uint8_t name[] = {'U'};
  memcpy(reg_info.name, name, reg_info.name_len);
  nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info);
  uint8_t event1 = 1, event2 = 2;
  tNFA_NDEF_EVT_DATA event_data1 = {};
  tNFA_NDEF_EVT_DATA event_data2 = {};
  uint8_t msg_buf1[sizeof(tNFA_NDEF_EVT_DATA)];
  uint8_t msg_buf2[sizeof(tNFA_NDEF_EVT_DATA)];
  memcpy(msg_buf1, &event_data1, sizeof(tNFA_NDEF_EVT_DATA));
  memcpy(msg_buf2, &event_data2, sizeof(tNFA_NDEF_EVT_DATA));
  EXPECT_CALL(mock_handler, OnNDEFData(event1, &event_data1)).Times(0);
  EXPECT_CALL(mock_handler, OnNDEFData(event2, &event_data2)).Times(0);
  uint32_t len = sizeof(tNFA_NDEF_EVT_DATA);
  tNFA_STATUS status = NFA_STATUS_OK;
  nfa_dm_ndef_handle_message(status, msg_buf1, len);
  nfa_dm_ndef_handle_message(status, msg_buf2, len);
}

//nfa_dm_ndef_find_next_handler

TEST_F(NfaDmTest, FindNextHandler_Success) {
  nfa_dm_cb = nfa_dm_cb_mock;
  tNFA_DM_API_REG_NDEF_HDLR reg_info1 = {};
  reg_info1.p_ndef_cback = NDEFCallbackBridge;
  reg_info1.tnf = NFA_TNF_DEFAULT;
  reg_info1.name_len = 1;
  uint8_t name1[] = {'1'};
  memcpy(reg_info1.name, name1, reg_info1.name_len);
  nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info1);
  tNFA_DM_API_REG_NDEF_HDLR reg_info2 = {};
  reg_info2.p_ndef_cback = NDEFCallbackBridge;
  reg_info2.tnf = NFA_TNF_DEFAULT;
  reg_info2.name_len = 1;
  uint8_t name2[] = {'2'};
  memcpy(reg_info2.name, name2, reg_info2.name_len);
  nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info2);
  unsigned char event = 1;
  unsigned char* p_name = nullptr;
  unsigned char name_len = 0;
  unsigned char* p_tnf = nullptr;
  unsigned int index = 0;
  bool result =
      nfa_dm_ndef_find_next_handler((tNFA_DM_API_REG_NDEF_HDLR*)&reg_info1,
                                    event, p_name, name_len, p_tnf, index);
  EXPECT_FALSE(result);
  result = nfa_dm_ndef_find_next_handler((tNFA_DM_API_REG_NDEF_HDLR*)&reg_info2,
                                         event, p_name, name_len, p_tnf, index);
  EXPECT_FALSE(result);
}

TEST_F(NfaDmTest, FindNextHandler_NoHandler) {
  nfa_dm_cb = nfa_dm_cb_mock;
  unsigned char event = 1;
  unsigned char* p_name = nullptr;
  unsigned char name_len = 0;
  unsigned char* p_tnf = nullptr;
  unsigned int index = 0;
  tNFA_DM_API_REG_NDEF_HDLR* found_handler = nullptr;
  bool result = nfa_dm_ndef_find_next_handler(nullptr, event, p_name, name_len,
                                              p_tnf, index);
  EXPECT_FALSE(result);
}

TEST_F(NfaDmTest, FindNextHandler_NoMatch) {
  nfa_dm_cb = nfa_dm_cb_mock;
  tNFA_DM_API_REG_NDEF_HDLR reg_info1 = {};
  reg_info1.p_ndef_cback = NDEFCallbackBridge;
  reg_info1.tnf = NFA_TNF_DEFAULT;
  reg_info1.name_len = 1;
  uint8_t name1[] = {'1'};
  memcpy(reg_info1.name, name1, reg_info1.name_len);
  nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info1);
  unsigned char event = 1;
  unsigned char* p_name = (unsigned char*)"NonMatchingName";
  unsigned char name_len = 15;
  unsigned char* p_tnf = nullptr;
  unsigned int index = 0;
  tNFA_DM_API_REG_NDEF_HDLR* found_handler = nullptr;
  bool result =
      nfa_dm_ndef_find_next_handler((tNFA_DM_API_REG_NDEF_HDLR*)&reg_info1,
                                    event, p_name, name_len, p_tnf, index);
  EXPECT_FALSE(result);
}

TEST_F(NfaDmTest, FindNextHandler_InvalidEvent) {
  nfa_dm_cb = nfa_dm_cb_mock;
  tNFA_DM_API_REG_NDEF_HDLR reg_info1 = {};
  reg_info1.p_ndef_cback = NDEFCallbackBridge;
  reg_info1.tnf = NFA_TNF_DEFAULT;
  reg_info1.name_len = 1;
  uint8_t name1[] = {'1'};
  memcpy(reg_info1.name, name1, reg_info1.name_len);
  nfa_dm_ndef_reg_hdlr((tNFA_DM_MSG*)&reg_info1);
  unsigned char invalid_event = 99;
  unsigned char* p_name = nullptr;
  unsigned char name_len = 0;
  unsigned char* p_tnf = nullptr;
  unsigned int index = 0;
  tNFA_DM_API_REG_NDEF_HDLR* found_handler = nullptr;
  bool result = nfa_dm_ndef_find_next_handler(
      (tNFA_DM_API_REG_NDEF_HDLR*)&reg_info1, invalid_event, p_name, name_len,
      p_tnf, index);
  EXPECT_FALSE(result);
}

//nfa_dm_ndef_find_next_handler

TEST_F(NfaDmTest, FindHandler_NoHandlerFound) {
    std::memset(&nfa_dm_cb, 0, sizeof(nfa_dm_cb));
    uint8_t tnf = NFA_TNF_DEFAULT;
    uint8_t* p_type_name = nullptr;
    uint8_t type_name_len = 0;
    uint8_t* p_payload = nullptr;
    uint32_t payload_len = 0;
    tNFA_DM_API_REG_NDEF_HDLR* found_handler = nfa_dm_ndef_find_next_handler(
            nullptr, tnf, p_type_name, type_name_len, p_payload, payload_len);
    EXPECT_EQ(found_handler, nullptr);
}

TEST_F(NfaDmTest, FindNextHandlerWithTNFMatch) {
    std::memset(&nfa_dm_cb, 0, sizeof(nfa_dm_cb));
    tNFA_DM_API_REG_NDEF_HDLR handler = {};
    handler.tnf = NFA_TNF_DEFAULT;
    nfa_dm_cb.p_ndef_handler[1] = &handler;
    tNFA_DM_API_REG_NDEF_HDLR* p_result = nfa_dm_ndef_find_next_handler(
            nullptr, NFA_TNF_DEFAULT, nullptr, 0, nullptr, 0);
    ASSERT_EQ(p_result, &handler);
}

TEST_F(NfaDmTest, FindNextHandlerWithNoMatchingTNF) {
    std::memset(&nfa_dm_cb, 0, sizeof(nfa_dm_cb));
    tNFA_DM_API_REG_NDEF_HDLR handler1 = {};
    handler1.tnf = NFA_TNF_DEFAULT;
    nfa_dm_cb.p_ndef_handler[1] = &handler1;
    tNFA_DM_API_REG_NDEF_HDLR handler2 = {};
    handler2.tnf = NFA_TNF_DEFAULT;
    nfa_dm_cb.p_ndef_handler[2] = &handler2;
    tNFA_DM_API_REG_NDEF_HDLR* p_result = nfa_dm_ndef_find_next_handler(
            nullptr, NFA_TNF_DEFAULT, nullptr, 0, nullptr, 0);
    ASSERT_EQ(p_result, &handler1);
}

TEST_F(NfaDmTest, FindNextHandlerAfterInitialHandler) {
    std::memset(&nfa_dm_cb, 0, sizeof(nfa_dm_cb));
    tNFA_DM_API_REG_NDEF_HDLR handler1 = {};
    handler1.tnf = NFA_TNF_WKT;
    handler1.ndef_type_handle = 1;
    nfa_dm_cb.p_ndef_handler[1] = &handler1;
    tNFA_DM_API_REG_NDEF_HDLR handler2 = {};
    handler2.tnf = NFA_TNF_DEFAULT;
    handler2.ndef_type_handle = 2;
    nfa_dm_cb.p_ndef_handler[2] = &handler2;
    tNFA_DM_API_REG_NDEF_HDLR* p_result = nfa_dm_ndef_find_next_handler(
            &handler1, NFA_TNF_DEFAULT, nullptr, 0, nullptr, 0);
    ASSERT_EQ(p_result, &handler2);
}

TEST_F(NfaDmTest, FindNextHandlerWithURIHandlerMismatch) {
    std::memset(&nfa_dm_cb, 0, sizeof(nfa_dm_cb));
    tNFA_DM_API_REG_NDEF_HDLR handler = {};
    handler.tnf = NFA_TNF_WKT;
    handler.flags = NFA_NDEF_FLAGS_WKT_URI;
    handler.uri_id = 1;
    nfa_dm_cb.p_ndef_handler[1] = &handler;
    uint8_t type_name[] = {'U'};
    uint8_t payload[] = {2};
    tNFA_DM_API_REG_NDEF_HDLR* p_result = nfa_dm_ndef_find_next_handler(
            nullptr, NFA_TNF_WKT, type_name, 1, payload, sizeof(payload)
    );
    ASSERT_EQ(p_result, nullptr);
}

// nfa_dm_ndef_clear_notified_flag

TEST_F(NfaDmTest, ClearNotifiedFlag_Success) {
    mock_handler1.flags |= NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED;
    mock_handler2.flags |= NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED;
    nfa_dm_ndef_clear_notified_flag();
    EXPECT_TRUE(mock_handler1.flags & NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED);
    EXPECT_TRUE(mock_handler2.flags & NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED);
}

TEST_F(NfaDmTest, ClearNotifiedFlag_AlreadyClear) {
    mock_handler1.flags &= ~NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED;
    mock_handler2.flags &= ~NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED;
    nfa_dm_ndef_clear_notified_flag();
    EXPECT_FALSE(mock_handler1.flags & NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED);
    EXPECT_FALSE(mock_handler2.flags & NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED);
}

TEST_F(NfaDmTest, ClearNotifiedFlag_OnlyRegisteredHandlers) {
    mock_handler1.flags |= NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED;
    nfa_dm_ndef_clear_notified_flag();
    EXPECT_TRUE(mock_handler1.flags & NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED);
    EXPECT_FALSE(mock_handler2.flags & NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED);
}

TEST_F(NfaDmTest, ClearNotifiedFlag_NoHandlers) {
    nfa_dm_cb_mock.p_ndef_handler[0] = nullptr;
    nfa_dm_cb_mock.p_ndef_handler[1] = nullptr;
    nfa_dm_ndef_clear_notified_flag();
    EXPECT_FALSE(mock_handler1.flags & NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED);
    EXPECT_FALSE(mock_handler2.flags & NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED);
}

TEST_F(NfaDmTest, ClearNotifiedFlag_MultipleCalls) {
    mock_handler1.flags |= NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED;
    mock_handler2.flags |= NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED;
    nfa_dm_ndef_clear_notified_flag();
    nfa_dm_ndef_clear_notified_flag();
    EXPECT_TRUE(mock_handler1.flags & NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED);
    EXPECT_TRUE(mock_handler2.flags & NFA_NDEF_FLAGS_WHOLE_MESSAGE_NOTIFIED);
}
