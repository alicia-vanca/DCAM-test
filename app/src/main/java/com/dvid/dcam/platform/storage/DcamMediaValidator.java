package com.dvid.dcam.platform.storage;

import java.io.File;

interface DcamMediaValidator {
    DcamMediaValidationResult validate(DcamFileType type, File file);

    default DcamMediaValidationResult validate(
            DcamFileType type, DcamRandomAccessMedia media) {
        return DcamMediaValidationResult.rejected(
                "Logical media validation is unsupported.");
    }
}