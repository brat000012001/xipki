/*
 *
 * Copyright (c) 2013 - 2017 Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.commons.security;

import java.util.Base64;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.operator.RuntimeOperatorException;
import org.bouncycastle.util.encoders.Hex;
import org.xipki.commons.common.util.ParamUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

class HashCalculator {

    private static final int PARALLELISM = 50;

    private static final ConcurrentHashMap<HashAlgoType, BlockingDeque<Digest>> MDS_MAP =
            new ConcurrentHashMap<>();

    static {
        MDS_MAP.put(HashAlgoType.SHA1, getMessageDigests(HashAlgoType.SHA1));
        MDS_MAP.put(HashAlgoType.SHA224, getMessageDigests(HashAlgoType.SHA224));
        MDS_MAP.put(HashAlgoType.SHA256, getMessageDigests(HashAlgoType.SHA256));
        MDS_MAP.put(HashAlgoType.SHA384, getMessageDigests(HashAlgoType.SHA384));
        MDS_MAP.put(HashAlgoType.SHA512, getMessageDigests(HashAlgoType.SHA512));
        MDS_MAP.put(HashAlgoType.SHA3_224, getMessageDigests(HashAlgoType.SHA3_224));
        MDS_MAP.put(HashAlgoType.SHA3_256, getMessageDigests(HashAlgoType.SHA3_256));
        MDS_MAP.put(HashAlgoType.SHA3_384, getMessageDigests(HashAlgoType.SHA3_384));
        MDS_MAP.put(HashAlgoType.SHA3_512, getMessageDigests(HashAlgoType.SHA3_512));
    }

    private HashCalculator() {
    }

    private static BlockingDeque<Digest> getMessageDigests(final HashAlgoType hashAlgo) {
        BlockingDeque<Digest> mds = new LinkedBlockingDeque<>();
        for (int i = 0; i < PARALLELISM; i++) {
            Digest md = hashAlgo.createDigest();
            mds.addLast(md);
        }
        return mds;
    }

    public static String base64Sha1(final byte[] data) {
        return base64Hash(HashAlgoType.SHA1, data);
    }

    public static String hexSha1(final byte[] data) {
        return hexHash(HashAlgoType.SHA1, data);
    }

    public static byte[] sha1(final byte[] data) {
        return hash(HashAlgoType.SHA1, data);
    }

    public static String base64Sha256(final byte[] data) {
        return base64Hash(HashAlgoType.SHA256, data);
    }

    public static String hexSha256(final byte[] data) {
        return hexHash(HashAlgoType.SHA256, data);
    }

    public static byte[] sha256(final byte[] data) {
        return hash(HashAlgoType.SHA256, data);
    }

    public static String hexHash(final HashAlgoType hashAlgoType, final byte[] data) {
        byte[] bytes = hash(hashAlgoType, data);
        return (bytes == null) ? null : Hex.toHexString(bytes).toUpperCase();
    }

    public static String base64Hash(final HashAlgoType hashAlgoType, final byte[] data) {
        byte[] bytes = hash(hashAlgoType, data);
        return (bytes == null) ? null : Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] hash(final HashAlgoType hashAlgoType, final byte[] data) {
        ParamUtil.requireNonNull("hashAlgoType", hashAlgoType);
        ParamUtil.requireNonNull("data", data);
        if (!MDS_MAP.containsKey(hashAlgoType)) {
            throw new IllegalArgumentException("unknown hash algo " + hashAlgoType);
        }

        BlockingDeque<Digest> mds = MDS_MAP.get(hashAlgoType);

        Digest md = null;
        for (int i = 0; i < 3; i++) {
            try {
                md = mds.poll(10, TimeUnit.SECONDS);
                break;
            } catch (InterruptedException ex) { // CHECKSTYLE:SKIP
            }
        }

        if (md == null) {
            throw new RuntimeOperatorException("could not get idle MessageDigest");
        }

        try {
            md.reset();
            md.update(data, 0, data.length);
            byte[] bytes = new byte[md.getDigestSize()];
            md.doFinal(bytes, 0);
            return bytes;
        } finally {
            mds.addLast(md);
        }
    } // method hash

}
