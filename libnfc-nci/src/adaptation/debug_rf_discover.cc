/**
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "include/debug_rf_discover.h"

#include <android-base/logging.h>
#include <android-base/stringprintf.h>

using android::base::StringPrintf;

/* The payload of each RF_DISCOVER_CMD */
rf_discover_payload_t rf_discover_payloads;

/* The committed rf discover config stored  */
std::vector<uint8_t> committed_rf_discover_configs(0);

/*******************************************************************************
**
** Function         debug_rf_discover_init
**
** Description      initialize the rf_discover_payloads
**
** Returns          None
**
*******************************************************************************/
void debug_rf_discover_init(void) {
  std::vector<uint8_t> empty_configs(0);

  rf_discover_payloads.entry_count = 0;
  rf_discover_payloads.configs.swap(empty_configs);
}

/*******************************************************************************
**
** Function         rf_discover_log
**
** Description      print the rf discover configuration for debug use
**
** Returns          None
**
*******************************************************************************/
void rf_discover_log(void) {
  if (!WOULD_LOG(VERBOSE)) return;

  static const char hexmap[] = {'0', '1', '2', '3', '4', '5', '6', '7',
                                '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

  std::string configs_str;
  for (uint8_t byte : rf_discover_payloads.configs) {
    configs_str.push_back(hexmap[byte >> 4]);
    configs_str.push_back(hexmap[byte & 0x0F]);
  }

  LOG(VERBOSE) << StringPrintf("%s: %d entries in this packet", __func__,
                               rf_discover_payloads.entry_count);

  LOG(VERBOSE) << StringPrintf("%s: configs=%s", __func__, configs_str.c_str());
}

/*******************************************************************************
**
** Function         rf_discover_capture
**
** Description      record the last RF_DISCOVER_CMD
**
** Returns          None
**
*******************************************************************************/
void rf_discover_capture(uint8_t* buf, uint8_t buf_size) {
  if (buf == nullptr || buf_size < 5) return;

  debug_rf_discover_init();

  rf_discover_payloads.entry_count = buf[3];
  std::vector<uint8_t> new_configs(buf + 4, buf + buf_size);
  rf_discover_payloads.configs.swap(new_configs);
}

/*******************************************************************************
**
** Function         rf_discover_update
**
** Description      Update the committed configs to
*committed_rf_discover_configs
**
** Returns          None
**
*******************************************************************************/
void rf_discover_update(void) {
  rf_discover_log();

  committed_rf_discover_configs.swap(rf_discover_payloads.configs);
}

/*******************************************************************************
**
** Function         lmrt_get_tlvs
**
** Description      This function is used to get the committed listen mode
**                  routing configuration command
**
** Returns          The committed listen mode routing configuration command
**
*******************************************************************************/
std::vector<uint8_t>* rf_discover_get_configs() {
  return &committed_rf_discover_configs;
}
