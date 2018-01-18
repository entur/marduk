package no.rutebanken.marduk.routes.mapbox;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

public class AwsClient {


    public AmazonS3 createClient(MapBoxAwsCredentials mapBoxAwsCredentials) {
        AWSCredentials credentials = new BasicSessionCredentials(mapBoxAwsCredentials.getAccessKeyId(), mapBoxAwsCredentials.getSecretAccessKey(), mapBoxAwsCredentials.getSessionToken());

        return new AmazonS3Client(credentials);
    }



}
