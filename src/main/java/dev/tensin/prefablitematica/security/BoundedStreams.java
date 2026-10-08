// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.security;

import java.io.*;
import java.util.zip.GZIPInputStream;

public final class BoundedStreams {
    private BoundedStreams() {}
    public static byte[] expand(byte[] compressed, int maxCompressed, int maxExpanded) throws IOException {
        if (compressed.length > maxCompressed) throw new IOException("Upload too large");
        try (var in = new GZIPInputStream(new ByteArrayInputStream(compressed)); var out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() > maxExpanded - count) throw new IOException("Expanded blueprint exceeds limit");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }
}
