/*
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
package com.android.nfc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HexFormat;

@RunWith(AndroidJUnit4.class)
public class ExitFrameTest {

    @Test
    public void testBasicFilter() {
        ExitFrame frame = new ExitFrame("aa11bb22");

        assertArrayEquals(HexFormat.of().parseHex("aa11bb22"), frame.getData());
        assertArrayEquals(HexFormat.of().parseHex("FFFFFFFF"), frame.getDataMask());
        assertEquals(0b00, frame.getNfcTech());
        assertEquals(0b00111001, frame.getPowerState());
        assertFalse(frame.isPrefixMatchingAllowed());
    }

    @Test
    public void testPrefixMatching() {
        ExitFrame frame = new ExitFrame("aa11.*");

        assertArrayEquals(HexFormat.of().parseHex("aa11"), frame.getData());
        assertArrayEquals(HexFormat.of().parseHex("FFFF"), frame.getDataMask());
        assertTrue(frame.isPrefixMatchingAllowed());
    }

    @Test
    public void testPrefixMatchingMaxLength() {
        ExitFrame frame = new ExitFrame("00112233445566778899AABBCCDDEEFF.*");

        assertArrayEquals(HexFormat.of().parseHex("00112233445566778899AABBCCDDEEFF"),
                frame.getData());
        assertTrue(frame.isPrefixMatchingAllowed());
    }

    @Test
    public void testPrefixMatchingMidByte() {
        ExitFrame frame = new ExitFrame("123.*");

        assertArrayEquals(HexFormat.of().parseHex("1230"), frame.getData());
        assertArrayEquals(HexFormat.of().parseHex("FFF0"), frame.getDataMask());
        assertTrue(frame.isPrefixMatchingAllowed());
    }

    @Test
    public void testMatchEverything() {
        ExitFrame frame = new ExitFrame("*");
        assertEquals(0, frame.getData().length);
        assertTrue(frame.isPrefixMatchingAllowed());
    }

    @Test
    public void testDataMask() {
        ExitFrame frame = new ExitFrame("123.56..");

        assertArrayEquals(HexFormat.of().parseHex("12305600"), frame.getData());
        assertArrayEquals(HexFormat.of().parseHex("FFF0FF00"), frame.getDataMask());
        assertFalse(frame.isPrefixMatchingAllowed());
    }

    @Test
    public void testPrefixAndMaskCombined() {
        ExitFrame frame = new ExitFrame("123.56.*");

        assertArrayEquals(HexFormat.of().parseHex("123056"), frame.getData());
        assertArrayEquals(HexFormat.of().parseHex("FFF0FF"), frame.getDataMask());
        assertTrue(frame.isPrefixMatchingAllowed());
    }

    @Test
    public void testStarInMiddleOfFilter() {
        assertThrows(IllegalArgumentException.class, () -> new ExitFrame("123*56"));
    }

    @Test
    public void testStarWithoutDot() {
        assertThrows(IllegalArgumentException.class, () -> new ExitFrame("1234*"));
    }

    @Test
    public void testTooLong() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExitFrame("00112233445566778899AABBCCDDEEFF00"));
    }

    @Test
    public void testQuestionMarkInFilter() {
        assertThrows(IllegalArgumentException.class, () -> new ExitFrame("123?"));
    }

    @Test
    public void testOddNumberOfCharactersNoStar() {
        assertThrows(IllegalArgumentException.class, () -> new ExitFrame("123"));
    }
}
