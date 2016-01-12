#!/bin/bash

S3_ACCESS_KEY=AKIAIBRVMDRJKQBK6YEQ
S3_SECRET_KEY=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

ACTIVEMQ_HOST=localhost
ACTIVEMQ_PORT=8161
ACTIVEMQ_USER=admin
ACTIVEMQ_PASSWORD=admin

rm -rf otpdata
mkdir otpdata

cat <<'EOF' >> otpdata/build-config.json
{
  "areaVisibility": true
}'
EOF

cat <<'EOF' >> otpdata/router-config.json
{
  "routingDefaults": {
      "walkSpeed": 1.3,
      "transferSlack": 120,
      "maxTransfers": 4,
      "waitReluctance": 0.95,
      "waitAtBeginningFactor": 0.7,
      "walkReluctance": 1.75,
      "stairsReluctance": 1.65,
      "walkBoardCost": 540
  },

  "boardTimes": {
    "AIRPLANE": 2700
  },
  "alightTimes": {
    "AIRPLANE": 1200
  },

  "updaters": [
    /* KOLUMBUS */
    {
          "type": "real-time-alerts",
          "frequencySec": 60,
          "url": "http://siri-kolumbus:8081/kolumbus/alerts",
          "defaultAgencyId": "Kolumbus"
      },
    {
        "type": "stop-time-updater",
        "frequencySec": 60,
        "sourceType": "gtfs-http",
        "url": "http://siri-kolumbus:8081/kolumbus/trip-updates",
        "defaultAgencyId": "Kolumbus"

    },
    /* ATB */
    {
          "type": "real-time-alerts",
          "frequencySec": 60,
          "url": "http://siri-atb:8081/atb/alerts",
          "defaultAgencyId": "AtB"
      },
    {
        "type": "stop-time-updater",
        "frequencySec": 60,
        "sourceType": "gtfs-http",
        "url": "http://siri-atb:8081/atb/trip-updates",
        "defaultAgencyId": "AtB"

      },
      /* Ruter (kun SX) */
      {
            "type": "real-time-alerts",
            "frequencySec": 60,
            "url": "http://siri-ruter:8081/ruter/alerts",
            "defaultAgencyId": "A"
    }
  ]
}
EOF


s3cmd --access_key=$S3_ACCESS_KEY --secret_key=$S3_SECRET_KEY get s3://junit-test-rutebanken/gtfs-exported/* otpdata

curl -o norway-latest.osm.pbf -z norway-latest.osm.pbf http://download.geofabrik.de/europe/norway-latest.osm.pbf
cp norway-latest.osm.pbf otpdata


#curl -o  otpdata/norway-latest.osm.pbf --create-dirs http://download.geofabrik.de/europe/norway-latest.osm.pbf
curl -o otp-da45a1c-rutebanken_14_12_2015.jar -z otp-da45a1c-rutebanken_14_12_2015.jar http://jump.rutebanken.org/otp-da45a1c-rutebanken_14_12_2015.jar

java -jar -Dfile.encoding=UTF-8 -server -Xmx4G otp-da45a1c-rutebanken_14_12_2015.jar --build otpdata || OTP_STATUS="${?}"
if [ $OTP_STATUS != 0 ]
then
MSG=ID:$CORRELATION_ID,RESULT:NOK
else
	s3cmd --access_key=$S3_ACCESS_KEY --secret_key=$S3_SECRET_KEY put otpdata/Graph.obj s3://junit-test-rutebanken/otp-graph_out/
	if [ ${?} == 0  ]
	then
	MSG=ID:$CORRELATION_ID,RESULT:OK
	fi
fi
echo Sending: $MSG
#TODO Find out how to address activemq
#curl -XPOST -d $MSG http://$ACTIVEMQ_USER:$ACTIVEMQ_PASSWORD@$ACTIVEMQ_HOST:$ACTIVEMQ_PORT/api/message?destination=queue://queue:OtpGraphStatusQueue
echo Exiting with otp status: $OTP_STATUS
exit $OTP_STATUS
