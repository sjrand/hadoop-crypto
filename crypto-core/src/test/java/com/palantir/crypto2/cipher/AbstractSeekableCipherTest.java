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

package com.palantir.crypto2.cipher;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.palantir.crypto2.keys.KeyMaterial;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.Random;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import org.apache.commons.crypto.cipher.CryptoCipher;
import org.apache.commons.crypto.cipher.CryptoCipherFactory;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractSeekableCipherTest {

    private static final int NUM_BLOCKS = 1000;
    private static final Random random = new Random(0);

    private KeyMaterial keyMaterial;
    private SeekableCipher seekableCipher;
    private CryptoCipher encryptCipher;
    private CryptoCipher decryptCipher;

    abstract KeyMaterial generateKeyMaterial();

    abstract SeekableCipher getCipher(KeyMaterial initKeyMaterial);

    abstract String getAlgorithm();

    private void initCipherUsingSeekableCipher(int mode, CryptoCipher cipher) throws InvalidKeyException,
            InvalidAlgorithmParameterException {
        cipher.init(mode,
                seekableCipher.getKeyMaterial().getSecretKey(),
                seekableCipher.getCurrIv());
    }

    @Before
    public final void before() throws GeneralSecurityException {
        keyMaterial = generateKeyMaterial();
        seekableCipher = getCipher(keyMaterial);

        encryptCipher = CryptoCipherFactory.getCryptoCipher(getAlgorithm());
        initCipherUsingSeekableCipher(Cipher.ENCRYPT_MODE, encryptCipher);

        decryptCipher = CryptoCipherFactory.getCryptoCipher(getAlgorithm());
        initCipherUsingSeekableCipher(Cipher.DECRYPT_MODE, decryptCipher);
    }

    @Test
    public final void testEncryptDecrypt_noSeek() throws BadPaddingException, IllegalBlockSizeException,
            ShortBufferException {
        testEncryptDecrypt(encryptCipher, decryptCipher);
    }

    @Test
    public final void testEncryptDecrypt_seekMaxValue() throws GeneralSecurityException {
        long offset = Long.MAX_VALUE / seekableCipher.getBlockSize() * seekableCipher.getBlockSize();

        seekableCipher.updateIvForNewPosition(offset);
        initCipherUsingSeekableCipher(Cipher.ENCRYPT_MODE, encryptCipher);
        initCipherUsingSeekableCipher(Cipher.DECRYPT_MODE, decryptCipher);

        testEncryptDecrypt(encryptCipher, decryptCipher);
    }

    @Test
    public final void testSeek() throws GeneralSecurityException {
        int blockSize = seekableCipher.getBlockSize();
        byte[] data = new byte[blockSize * NUM_BLOCKS];
        byte val = 0x01;

        int lastBlock = NUM_BLOCKS - 1;
        int lastBlockOffset = lastBlock * blockSize;
        int prevBlockOffset = lastBlockOffset - blockSize;

        // Create large array of form { 0x00, 0x00, ... , 0x00, 0x01, ... blockSize - 2 ... ,0x01 }
        Arrays.fill(data, (byte) 0x00);
        Arrays.fill(data, lastBlockOffset, lastBlockOffset + blockSize, val);

        byte[] encryptedData = new byte[blockSize * (NUM_BLOCKS + 1)];
        encryptCipher.doFinal(data, 0, data.length, encryptedData, 0);

        seekableCipher.updateIvForNewPosition(prevBlockOffset);
        initCipherUsingSeekableCipher(Cipher.DECRYPT_MODE, decryptCipher);

        // Decrypt from block n - 1 to the end of the encrypted data
        byte[] lastBlocksData = new byte[blockSize * 3];
        decryptCipher.doFinal(encryptedData, prevBlockOffset, encryptedData.length - prevBlockOffset,
                lastBlocksData, 0);
        byte[] lastBlockData = Arrays.copyOfRange(lastBlocksData, blockSize, 2 * blockSize);

        byte[] expected = new byte[blockSize];
        Arrays.fill(expected, val);

        assertThat(lastBlockData, is(expected));
    }

    @Test
    public final void testSeek_seekNegativeValue() {
        long negPos = -1;
        try {
            seekableCipher.updateIvForNewPosition(negPos);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is(String.format("Cannot seek to negative position: %d", negPos)));
        }
    }

    @Test
    public final void testGetKeyMaterial() {
        assertThat(seekableCipher.getKeyMaterial(), is(keyMaterial));
    }


    public final void testEncryptDecrypt(CryptoCipher encryptingCipher, CryptoCipher decryptingCipher)
            throws BadPaddingException, IllegalBlockSizeException, ShortBufferException {
        byte[] data = new byte[NUM_BLOCKS * encryptingCipher.getBlockSize()];
        random.nextBytes(data);

        // Account for padding or the lack thereof.
        byte[] encryptedData;
        if (getAlgorithm().equals(AesCtrCipher.ALGORITHM)) {
            encryptedData = new byte[encryptingCipher.getBlockSize() * NUM_BLOCKS];
        } else if (getAlgorithm().equals(AesCbcCipher.ALGORITHM)) {
            encryptedData = new byte[encryptingCipher.getBlockSize() * (NUM_BLOCKS + 1)];
        } else {
            throw new IllegalArgumentException("Must specify either \"AES/CBC/PKCS5Padding\""
                    + "or \"AES/CTR/NoPadding\"");
        }
        encryptingCipher.doFinal(data, 0, data.length, encryptedData, 0);

        byte[] decryptedData = new byte[NUM_BLOCKS * decryptingCipher.getBlockSize()];
        decryptingCipher.update(encryptedData, 0, encryptedData.length, decryptedData, 0);

        assertThat(data, is(not(encryptedData)));
        assertThat(data, is(decryptedData));
    }

}
