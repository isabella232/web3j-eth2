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
package org.web3j.beacon.api.response.v1.validator

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import tech.pegasys.teku.api.schema.Attestation

class GetAggregatedAttestationResponse @JsonCreator constructor(@JsonProperty("data") data: Attestation) {
    @JsonProperty("data")
    val data: Attestation
    fun getData(): Attestation {
        return data
    }

    init {
        this.data = data
    }
}
