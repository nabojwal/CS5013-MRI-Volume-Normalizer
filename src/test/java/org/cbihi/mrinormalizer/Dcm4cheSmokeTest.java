package org.cbihi.mrinormalizer;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class Dcm4cheSmokeTest {

    @Test
    void dcm4cheAttributesShouldWork() {
        Attributes attributes = new Attributes();

        attributes.setString(
                Tag.PatientName,
                VR.PN,
                "TEST^PATIENT"
        );

        assertEquals(
                "TEST^PATIENT",
                attributes.getString(Tag.PatientName)
        );
    }
}
