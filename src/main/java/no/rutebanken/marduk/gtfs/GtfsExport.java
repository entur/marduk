package no.rutebanken.marduk.gtfs;

import java.util.Map;

public enum GtfsExport {

    GTFS_BASIC_AND_EXTENDED(Map.of(
            GtfsConstants.AGENCY_TXT, "agency_id,agency_name,agency_url,agency_timezone,agency_phone".split(","),
            GtfsConstants.CALENDAR_TXT, "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date".split(","),
            GtfsConstants.CALENDAR_DATES_TXT, "service_id,date,exception_type".split(","),
            GtfsConstants.ROUTES_TXT, "agency_id,route_id,route_short_name,route_long_name,route_type,route_desc,route_url,route_color,route_text_color".split(","),
            GtfsConstants.SHAPES_TXT, "shape_id,shape_pt_sequence,shape_pt_lat,shape_pt_lon,shape_dist_traveled".split(","),
            GtfsConstants.STOPS_TXT, "stop_id,stop_name,stop_lat,stop_lon,stop_desc,location_type,parent_station,wheelchair_boarding,stop_timezone,vehicle_type,platform_code".split(","),
            GtfsConstants.STOP_TIMES_TXT, "trip_id,stop_id,arrival_time,departure_time,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled".split(","),
            GtfsConstants.TRANSFERS_TXT, "from_stop_id,from_trip_id,to_stop_id,to_trip_id,transfer_type".split(","),
            GtfsConstants.TRIPS_TXT, "route_id,trip_id,service_id,trip_headsign,direction_id,shape_id,wheelchair_accessible".split(","))),

    GTFS_GOOGLE(Map.of(
            GtfsConstants.AGENCY_TXT, "agency_id,agency_name,agency_url,agency_timezone,agency_phone".split(","),
            GtfsConstants.CALENDAR_TXT, "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date".split(","),
            GtfsConstants.CALENDAR_DATES_TXT, "service_id,date,exception_type".split(","),
            GtfsConstants.ROUTES_TXT, "agency_id,route_id,route_short_name,route_long_name,route_type,route_desc,route_color,route_text_color".split(","),
            GtfsConstants.SHAPES_TXT, "shape_id,shape_pt_sequence,shape_pt_lat,shape_pt_lon,shape_dist_traveled".split(","),
            GtfsConstants.STOPS_TXT, "stop_id,stop_name,stop_lat,stop_lon,stop_desc,location_type,parent_station,wheelchair_boarding,vehicle_type,platform_code".split(","),
            GtfsConstants.STOP_TIMES_TXT, "trip_id,stop_id,arrival_time,departure_time,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled".split(","),
            GtfsConstants.TRANSFERS_TXT, "from_stop_id,from_trip_id,to_stop_id,to_trip_id,transfer_type".split(","),
            GtfsConstants.TRIPS_TXT, "route_id,trip_id,service_id,trip_headsign,direction_id,shape_id,wheelchair_accessible".split(",")));

    private Map<String, String[]> headers;

    GtfsExport(Map<String, String[]> headers) {
        this.headers = headers;
    }

    public Map<String, String[]> getHeaders() {
        return headers;
    }
}
