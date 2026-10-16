package org.cbihi.mrinormalizer.infrastructure.nifti;

import static org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFailure.*;
import static org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFinding.*;
import static org.cbihi.mrinormalizer.infrastructure.nifti.Nifti1VolumeReader.require;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadFinding;
import org.cbihi.mrinormalizer.application.dataset.nifti.NiftiReadLimits;
import org.cbihi.mrinormalizer.domain.model.CoordinateSystem;
import org.cbihi.mrinormalizer.domain.model.IntensityTransform;
import org.cbihi.mrinormalizer.domain.model.ScalarType;
import org.cbihi.mrinormalizer.domain.model.VolumeGeometry;

/** Bounded NIfTI-1 header semantics. No extension text or patient metadata is retained. */
final class Nifti1Header {
    final ByteOrder order;
    final int width, height, depth, count, bytesPerVoxel;
    final long offset, decodedLength;
    final ScalarType type;
    final VolumeGeometry geometry;
    final IntensityTransform scaling;

    Nifti1Header(byte[] bytes, NiftiReadLimits limits, EnumSet<NiftiReadFinding> findings)
            throws Nifti1VolumeReader.Fault {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int little = b.getInt(0), big = b.order(ByteOrder.BIG_ENDIAN).getInt(0);
        require(little != 540 && big != 540, UNSUPPORTED_VARIANT);
        require(little == 348 || big == 348, INVALID_HEADER);
        order = little == 348 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        b.order(order);
        if (b.get(344) == 'n' && b.get(345) == 'i' && b.get(346) == '1' && b.get(347) == 0) {
            throw new Nifti1VolumeReader.Fault(UNSUPPORTED_VARIANT);
        }
        // Legacy ANALYZE headers have no NIfTI magic; never accept them as n+1.
        require(!(b.get(344) == 0 && b.get(345) == 0 && b.get(346) == 0 && b.get(347) == 0), UNSUPPORTED_VARIANT);
        require(b.get(344) == 'n' && b.get(345) == '+' && b.get(346) == '1' && b.get(347) == 0, INVALID_HEADER);
        require(b.getShort(40) == 3, UNSUPPORTED_DIMENSIONALITY);
        width = b.getShort(42); height = b.getShort(44); depth = b.getShort(46);
        require(width > 0 && height > 0 && depth > 0, UNSUPPORTED_DIMENSIONALITY);
        for (int i = 48; i <= 54; i += 2) {
            require(b.getShort(i) == 0 || b.getShort(i) == 1, UNSUPPORTED_DIMENSIONALITY);
        }
        require(b.getShort(68) == 0, UNSUPPORTED_INTENT);
        type = switch (b.getShort(70)) {
            case 2 -> ScalarType.UINT8;
            case 256 -> ScalarType.INT8;
            case 512 -> ScalarType.UINT16;
            case 4 -> ScalarType.INT16;
            default -> throw new Nifti1VolumeReader.Fault(UNSUPPORTED_DATATYPE);
        };
        bytesPerVoxel = type == ScalarType.UINT8 || type == ScalarType.INT8 ? 1 : 2;
        require(b.getShort(72) == bytesPerVoxel * 8, INVALID_HEADER);
        double off = b.getFloat(108);
        require(Double.isFinite(off) && off >= 352 && off == Math.rint(off), INVALID_HEADER);
        // Compare in double before conversion, including the Long.MAX_VALUE rounding edge.
        require(off < 0x1p63 && off <= limits.maxPrefixBytes(), RESOURCE_LIMIT);
        offset = (long) off;
        long n = Math.multiplyExact(Math.multiplyExact((long) width, height), depth);
        require(n <= limits.maxVoxels() && n <= Integer.MAX_VALUE - 8L, RESOURCE_LIMIT);
        count = (int) n;
        long payload = Math.multiplyExact(n, bytesPerVoxel);
        require(offset <= Long.MAX_VALUE - payload, RESOURCE_LIMIT);
        decodedLength = offset + payload;
        require(decodedLength <= limits.maxDecodedBytes(), RESOURCE_LIMIT);
        // Both the working array and ImmutableVoxelData's defensive clone, before either allocation.
        require(Math.addExact(Math.multiplyExact(16L, n), 1_048_576L) <= limits.maxWorkingBytes(), RESOURCE_LIMIT);
        int units = Byte.toUnsignedInt(b.get(123));
        require((units & 0xc0) == 0 && (units & 0x38) <= 48, INVALID_HEADER);
        double factor = switch (units & 7) {
            case 1 -> 1000;
            case 2 -> 1;
            case 3 -> .001;
            default -> throw new Nifti1VolumeReader.Fault(UNSUPPORTED_SPATIAL_UNITS);
        };
        double[] spacing = new double[3];
        for (int i = 0; i < 3; i++) {
            spacing[i] = b.getFloat(80 + i * 4) * factor;
            require(Double.isFinite(spacing[i]) && spacing[i] > 0, INVALID_HEADER);
        }
        double slope = b.getFloat(112), intercept = b.getFloat(116);
        require(Double.isFinite(slope) && (slope == 0 || Double.isFinite(intercept)), INVALID_SCALING);
        scaling = slope == 0 ? IntensityTransform.identityNotDeclared() : new IntensityTransform(true, slope, intercept);
        int scode = b.getShort(254), qcode = b.getShort(252);
        double[][] s = null, q = null;
        if (scode >= 1 && scode <= 5) {
            s = new double[3][4];
            for (int r = 0; r < 3; r++) for (int c = 0; c < 4; c++) s[r][c] = b.getFloat(280 + 16 * r + 4 * c) * factor;
            if (!usable(s)) s = null;
            if (s != null) for (int c = 0; c < 3; c++) {
                double length = norm(s, c);
                if (Math.abs(length - spacing[c]) > 1e-5 + 1e-6 * Math.max(length, spacing[c])) { s = null; break; }
            }
        }
        if (qcode >= 1 && qcode <= 5) q = quaternion(b, spacing, factor, findings);
        if (scode != 0 && s == null) findings.add(INVALID_SFORM_IGNORED);
        if (qcode != 0 && q == null) findings.add(INVALID_QFORM_IGNORED);
        require(s != null || q != null, GEOMETRY_UNAVAILABLE);
        if (s != null && q != null) require(agree(s, q), TRANSFORM_DISAGREEMENT);
        double[][] selected = s != null ? s : q;
        findings.add(s != null ? SFORM_SELECTED : QFORM_SELECTED);
        double[] norms = {norm(selected, 0), norm(selected, 1), norm(selected, 2)};
        geometry = new VolumeGeometry(width, height, depth, norms[1], norms[0], norms[2],
                new double[]{selected[0][3], selected[1][3], selected[2][3]},
                direction(selected, 0, norms[0]), direction(selected, 1, norms[1]), direction(selected, 2, norms[2]),
                CoordinateSystem.NIFTI_RAS);
    }

    private static double[] direction(double[][] m, int c, double n) {
        return new double[]{m[0][c] / n, m[1][c] / n, m[2][c] / n};
    }

    private static double norm(double[][] m, int c) {
        return Math.hypot(Math.hypot(m[0][c], m[1][c]), m[2][c]);
    }

    private static boolean usable(double[][] m) {
        for (var row : m) for (double v : row) if (!Double.isFinite(v)) return false;
        double[][] u = new double[3][3];
        for (int c = 0; c < 3; c++) {
            double n = norm(m, c);
            if (!(n > 0) || !Double.isFinite(n)) return false;
            for (int r = 0; r < 3; r++) u[r][c] = m[r][c] / n;
        }
        double det = u[0][0] * (u[1][1] * u[2][2] - u[1][2] * u[2][1])
                - u[0][1] * (u[1][0] * u[2][2] - u[1][2] * u[2][0])
                + u[0][2] * (u[1][0] * u[2][1] - u[1][1] * u[2][0]);
        return Math.abs(det) > 1e-8;
    }

    private static double[][] quaternion(ByteBuffer h, double[] spacing, double factor,
            EnumSet<NiftiReadFinding> findings) {
        double f = h.getFloat(76), b = h.getFloat(256), c = h.getFloat(260), d = h.getFloat(264);
        for (int o = 256; o <= 276; o += 4) if (!Float.isFinite(h.getFloat(o))) return null;
        if (f == 0) { f = 1; findings.add(QFAC_ZERO_AS_POSITIVE); }
        else if (f != 1 && f != -1) return null;
        double sum = b * b + c * c + d * d, a;
        if (sum <= 1) a = Math.sqrt(1 - sum);
        else if (sum <= 1 + 1e-6) {
            double n = Math.sqrt(sum); b /= n; c /= n; d /= n; a = 0;
            findings.add(QUATERNION_ROUNDING_NORMALIZED);
        } else return null;
        double[][] m = {
                {a*a+b*b-c*c-d*d, 2*(b*c-a*d), 2*(b*d+a*c), h.getFloat(268)*factor},
                {2*(b*c+a*d), a*a+c*c-b*b-d*d, 2*(c*d-a*b), h.getFloat(272)*factor},
                {2*(b*d-a*c), 2*(c*d+a*b), a*a+d*d-c*c-b*b, h.getFloat(276)*factor}
        };
        for (int r = 0; r < 3; r++) for (int col = 0; col < 3; col++) m[r][col] *= spacing[col] * (col == 2 ? f : 1);
        return usable(m) ? m : null;
    }

    private boolean agree(double[][] s, double[][] q) {
        double length = 0, displacement = 0;
        for (int mask = 0; mask < 8; mask++) {
            double[] v = {(mask & 1) == 0 ? 0 : Math.max(1, width - 1),
                    (mask & 2) == 0 ? 0 : Math.max(1, height - 1),
                    (mask & 4) == 0 ? 0 : Math.max(1, depth - 1)};
            double[] so = new double[3], qo = new double[3], delta = new double[3];
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) { so[r] += s[r][c] * v[c]; qo[r] += q[r][c] * v[c]; }
                // Subtract translations first: tolerance must not depend on absolute world origin.
                delta[r] = (s[r][3] - q[r][3]) + (so[r] - qo[r]);
            }
            length = Math.max(length, Math.max(length(so), length(qo)));
            displacement = Math.max(displacement, length(delta));
        }
        return displacement <= 1e-4 + 1e-6 * Math.max(1, length);
    }

    private static double length(double[] v) { return Math.hypot(Math.hypot(v[0], v[1]), v[2]); }
}
