package no.rutebanken.marduk.gtfs;

import java.util.Map;

/**
 * GTFS Export types.
 */
public enum GtfsExport {

    /**
     * GTFS export that contains non-standard route types.
     */
    GTFS_EXTENDED(GtfsHeaders.HEADERS_FOR_BASIC_AND_EXTENDED_EXPORT),

    /**
     * GTFS export that contains only standard route types.
     */
    GTFS_BASIC(GtfsHeaders.HEADERS_FOR_BASIC_AND_EXTENDED_EXPORT),

    /**
     * GTFS export for Google.
     */
    GTFS_GOOGLE(GtfsHeaders.HEADERS_FOR_GOOGLE_EXPORT);

    private Map<String, String[]> headers;

    GtfsExport(Map<String, String[]> headers) {
        this.headers = headers;
    }

    public Map<String, String[]> getHeaders() {
        return headers;
    }


    private static class GtfsHeaders {

        private GtfsHeaders() {
        }

        private static final String[] AGENCY_TXT_HEADERS = splitHeaders("agency_id,agency_name,agency_url,agency_timezone,agency_phone");
        private static final String[] CALENDAR_TXT_HEADERS = splitHeaders("service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date");
        private static final String[] CALENDAR_DATES_TXT_HEADERS = splitHeaders("service_id,date,exception_type");
        private static final String[] ROUTES_TXT_HEADERS = splitHeaders("agency_id,route_id,route_short_name,route_long_name,route_type,route_desc,route_url,route_color,route_text_color");
        private static final String[] SHAPES_TXT_HEADERS = splitHeaders("shape_id,shape_pt_sequence,shape_pt_lat,shape_pt_lon,shape_dist_traveled");
        private static final String[] STOPS_TXT_HEADERS = splitHeaders("stop_id,stop_name,stop_lat,stop_lon,stop_desc,location_type,parent_station,wheelchair_boarding,stop_timezone,vehicle_type,platform_code");
        private static final String[] STOP_TIMES_TXT_HEADERS = splitHeaders("trip_id,stop_id,arrival_time,departure_time,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled");
        private static final String[] TRANSFERS_TXT_HEADERS = splitHeaders("from_stop_id,from_trip_id,to_stop_id,to_trip_id,transfer_type");
        private static final String[] TRIPS_TXT_HEADERS = splitHeaders("route_id,trip_id,service_id,trip_headsign,direction_id,shape_id,wheelchair_accessible");

        // Headers used only for Google GTFS exports
        private static final String[] ROUTES_TXT_GOOGLE_HEADERS = splitHeaders("agency_id,route_id,route_short_name,route_long_name,route_type,route_desc,route_color,route_text_color");
        private static final String[] STOPS_TXT_GOOGLE_HEADERS = splitHeaders("stop_id,stop_name,stop_lat,stop_lon,stop_desc,location_type,parent_station,wheelchair_boarding,vehicle_type,platform_code");


        private static final Map<String, String[]> HEADERS_FOR_BASIC_AND_EXTENDED_EXPORT = Map.of(
                GtfsConstants.AGENCY_TXT, AGENCY_TXT_HEADERS,
                GtfsConstants.CALENDAR_TXT, CALENDAR_TXT_HEADERS,
                GtfsConstants.CALENDAR_DATES_TXT, CALENDAR_DATES_TXT_HEADERS,
                GtfsConstants.ROUTES_TXT, ROUTES_TXT_HEADERS,
                GtfsConstants.SHAPES_TXT, SHAPES_TXT_HEADERS,
                GtfsConstants.STOPS_TXT, STOPS_TXT_HEADERS,
                GtfsConstants.STOP_TIMES_TXT, STOP_TIMES_TXT_HEADERS,
                GtfsConstants.TRANSFERS_TXT, TRANSFERS_TXT_HEADERS,
                GtfsConstants.TRIPS_TXT, TRIPS_TXT_HEADERS);

        private static final Map<String, String[]> HEADERS_FOR_GOOGLE_EXPORT = Map.of(
                GtfsConstants.AGENCY_TXT, AGENCY_TXT_HEADERS,
                GtfsConstants.CALENDAR_TXT, CALENDAR_TXT_HEADERS,
                GtfsConstants.CALENDAR_DATES_TXT, CALENDAR_DATES_TXT_HEADERS,
                GtfsConstants.ROUTES_TXT, ROUTES_TXT_GOOGLE_HEADERS,
                GtfsConstants.SHAPES_TXT, SHAPES_TXT_HEADERS,
                GtfsConstants.STOPS_TXT, STOPS_TXT_GOOGLE_HEADERS,
                GtfsConstants.STOP_TIMES_TXT, STOP_TIMES_TXT_HEADERS,
                GtfsConstants.TRANSFERS_TXT, TRANSFERS_TXT_HEADERS,
                GtfsConstants.TRIPS_TXT, TRIPS_TXT_HEADERS);

        private static String[] splitHeaders(String header) {
            return header.split(",");
        }
    }

}
