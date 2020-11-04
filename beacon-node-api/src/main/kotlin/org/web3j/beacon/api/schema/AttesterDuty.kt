/*
 * Copyright 2020 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.web3j.beacon.api.schema

/**
 *
 * @param pubkey The validator's BLS public key, uniquely identifying them. _48-bytes, hex encoded with 0x prefix, case insensitive._
 * @param validatorIndex
 * @param committeeIndex
 * @param committeeLength
 * @param committeesAtSlot
 * @param validatorCommitteeIndex
 * @param slot
 */
data class AttesterDuty(

        /* The validator's BLS public key, uniquely identifying them. _48-bytes, hex encoded with 0x prefix, case insensitive._ */
    val pubkey: String? = null,
    val validatorIndex: AllOfAttesterDutyValidatorIndex? = null,
    val committeeIndex: AllOfAttesterDutyCommitteeIndex? = null,
    val committeeLength: AllOfAttesterDutyCommitteeLength? = null,
    val committeesAtSlot: AllOfAttesterDutyCommitteesAtSlot? = null,
    val validatorCommitteeIndex: AllOfAttesterDutyValidatorCommitteeIndex? = null,
    val slot: AllOfAttesterDutySlot? = null
)
