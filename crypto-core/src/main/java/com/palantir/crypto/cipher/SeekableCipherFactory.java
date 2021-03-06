/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.crypto.cipher;

import com.palantir.crypto.KeyMaterial;

/**
 * Constructs the proper {@link SeekableCipher} for a given {@code cipherAlgorithm} string. The {@link KeyMaterial} will
 * be generated if it is not provided.
 */
public final class SeekableCipherFactory {

    private SeekableCipherFactory() {}

    public static SeekableCipher getCipher(String cipherAlgorithm) {
        switch (cipherAlgorithm) {
            case AesCtrCipher.ALGORITHM:
                return getCipher(cipherAlgorithm, AesCtrCipher.generateKeyMaterial());
            case AesCbcCipher.ALGORITHM:
                return getCipher(cipherAlgorithm, AesCbcCipher.generateKeyMaterial());
            default:
                throw new IllegalArgumentException(
                        String.format("No known SeekableCipher with algorithm: %s", cipherAlgorithm));
        }
    }

    public static SeekableCipher getCipher(String cipherAlgorithm, KeyMaterial keyMaterial) {
        switch (cipherAlgorithm) {
            case AesCtrCipher.ALGORITHM: return new AesCtrCipher(keyMaterial);
            case AesCbcCipher.ALGORITHM: return new AesCbcCipher(keyMaterial);
            default:
                throw new IllegalArgumentException(
                        String.format("No known SeekableCipher with algorithm: %s", cipherAlgorithm));
        }
    }

}
