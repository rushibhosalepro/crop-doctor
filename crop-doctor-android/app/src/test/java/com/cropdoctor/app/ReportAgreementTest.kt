package com.cropdoctor.app

import com.cropdoctor.app.data.CloudVerification
import com.cropdoctor.app.data.FieldObservation
import com.cropdoctor.app.data.Report
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Agreement-level tests. Regression coverage for the report-readability fix:
 * a mere extent wording difference must show as a small detail difference,
 * never as an UPDATED ASSESSMENT alarm.
 * Run: ./gradlew :app:testDebugUnitTest
 */
class ReportAgreementTest {

    private fun report(obsExtent: String, cloud: CloudVerification?): Report {
        return Report(
            observation = FieldObservation(subject = "plant", extent = obsExtent),
            cloud = cloud
        )
    }

    @Test fun `same subject and extent is a match`() {
        val rep = report("small", CloudVerification(subject = "plant", extent = "small"))
        assertEquals("match", rep.agreementLevel())
    }

    @Test fun `same subject with different extent is a small diff, not an update`() {
        val rep = report("none", CloudVerification(subject = "plant", extent = "small"))
        assertEquals("small_diff", rep.agreementLevel())
    }

    @Test fun `different subject is an update`() {
        val rep = report("small", CloudVerification(subject = "not_plant", extent = "none"))
        assertEquals("updated", rep.agreementLevel())
    }

    @Test fun `missing cloud review reports none`() {
        val rep = report("small", null)
        assertEquals("none", rep.agreementLevel())
    }
}
