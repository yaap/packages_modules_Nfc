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

#ifndef _DEBUG_RF_DISCOVER_
#define _DEBUG_RF_DISCOVER_

#include <stdint.h>

#include <string>
#include <vector>

#include "nfc_int.h"

/* The type definition of the RF_DISCOVER_CMD */
typedef struct rf_discover_payload_t {
  uint8_t entry_count;
  std::vector<uint8_t> configs;
} __attribute__((__packed__)) rf_discover_payload_t;

/*******************************************************************************
**
** Function         debug_rf_discover_init
**
** Description      initialize the rf_discover_payloads
**
** Returns          None
**
*******************************************************************************/
void debug_rf_discover_init(void);

/*******************************************************************************
**
** Function         rf_discover_log
**
** Description      print the rf discover configuration for debug use
**
** Returns          None
**
*******************************************************************************/
void rf_discover_log(void);

/*******************************************************************************
**
** Function         rf_discover_capture
**
** Description      record the last RF_DISCOVER_CMD
**
** Returns          None
**
*******************************************************************************/
void rf_discover_capture(uint8_t* buf, uint8_t buf_size);

/*******************************************************************************
**
** Function         rf_discover_update
**
** Description      Update the committed tlvs
**
** Returns          None
**
*******************************************************************************/
void rf_discover_update(void);

/*******************************************************************************
**
** Function         rf_discover_get_configs
**
** Description      This function is used to get the committed rf discover
**                  configuration
**
** Returns          The committed rf discover configuration
**
*******************************************************************************/
std::vector<uint8_t>* rf_discover_get_configs();

#endif /* _DEBUG_RF_DISCOVER_ */
