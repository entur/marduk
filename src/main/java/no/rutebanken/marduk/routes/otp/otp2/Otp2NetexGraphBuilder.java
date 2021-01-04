
package no.rutebanken.marduk.routes.otp.otp2;

import io.fabric8.kubernetes.api.model.EnvVar;
import no.rutebanken.marduk.routes.otp.OtpGraphBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import static no.rutebanken.marduk.Constants.OTP2_BASE_GRAPH_CANDIDATE_OBJ;
import static no.rutebanken.marduk.Constants.OTP2_BASE_GRAPH_OBJ;


/**
 * Build an OTP full graph (with transit data) based on NeTEx input data.
 */
@Component
public class Otp2NetexGraphBuilder extends AbstractOtp2GraphBuilder implements OtpGraphBuilder {

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreGraphSubdirectory;

    protected List<EnvVar> getEnvVars(String otpWorkDir, boolean candidate) {
        final String baseGraphPath;
        if (candidate) {
            baseGraphPath = blobStoreGraphSubdirectory + "/" + OTP2_BASE_GRAPH_CANDIDATE_OBJ;
        } else {
            baseGraphPath = blobStoreGraphSubdirectory + "/" + OTP2_BASE_GRAPH_OBJ;
        }
        return List.of(
                new EnvVar(OTP_GCS_WORK_DIR_ENV_VAR, otpWorkDir, null),
                new EnvVar(OTP_GRAPH_MODE, "--loadStreet", null),
                new EnvVar(OTP_GCS_BASE_GRAPH_PATH_ENV_VAR, baseGraphPath, null),
                // TODO for backward compatibility, to be removed when OTP is updated
                new EnvVar(OTP_GCS_BASE_GRAPH_DIR_ENV_VAR, blobStoreGraphSubdirectory, null));
    }

    @Override
    protected String getJobNamePrefix() {
        return "otp2-graph-builder-netex";
    }

}
