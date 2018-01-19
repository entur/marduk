package no.rutebanken.marduk.routes.mapbox;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import no.rutebanken.marduk.routes.mapbox.model.MapBoxAwsCredentials;
import org.apache.camel.Body;
import org.apache.camel.Header;
import org.apache.camel.PropertyInject;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Implemented because of the requirement to use aws client with temporary session token.
 */
@Service
public class AwsS3Uploader {
    private static final Logger logger = LoggerFactory.getLogger(AwsS3Uploader.class);

    public AmazonS3Client createClient(MapBoxAwsCredentials mapBoxAwsCredentials) {
        AWSCredentials credentials = new BasicSessionCredentials(mapBoxAwsCredentials.getAccessKeyId(),
                mapBoxAwsCredentials.getSecretAccessKey(), mapBoxAwsCredentials.getSessionToken());

        return new AmazonS3Client(credentials);
    }

    public void upload(@Header("credentials") MapBoxAwsCredentials credentials,
                       @PropertyInject("filename") String filename,
                       @Body InputStream inputStream) throws IOException {
        logger.info("Uploading inputStream {} to aws. bucket: {}, key: {}, filename: {}", inputStream, credentials.getBucket(), credentials.getKey(), filename);
        AmazonS3Client amazonS3Client = createClient(credentials);
        amazonS3Client.setRegion(Region.getRegion(Regions.US_EAST_1));



        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType("application/json");
        objectMetadata.setContentLength(inputStream.available());

//        byte[] resultByte = DigestUtils.md5(inputStream);
//        String streamMD5 = new String(Base64.encodeBase64(resultByte));
//        objectMetadata.setContentMD5(streamMD5);

        objectMetadata.setContentDisposition(filename);

        logger.info(ToStringBuilder.reflectionToString(objectMetadata));
        amazonS3Client.putObject(credentials.getBucket(), credentials.getKey(), inputStream, objectMetadata);
    }
}
