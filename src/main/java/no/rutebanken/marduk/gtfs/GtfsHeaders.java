package no.rutebanken.marduk.gtfs;

import java.util.Map;

public class GtfsHeaders {

    public static final Map<String, String[]> GTFS_EXTENDED_HEADERS = Map.of(
            "agency.txt", "agency_id,agency_name,agency_url,agency_timezone,agency_phone".split(","),
            "calendar.txt", "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date".split(","),
            "calendar_dates.txt", "service_id,date,exception_type".split(","),
            "routes.txt", "agency_id,route_id,route_short_name,route_long_name,route_type,route_desc,route_color,route_text_color".split(","),
            "shapes.txt", "shape_id,shape_pt_sequence,shape_pt_lat,shape_pt_lon,shape_dist_traveled".split(","),
            "stops.txt", "stop_id,stop_name,stop_lat,stop_lon,stop_desc,location_type,parent_station,wheelchair_boarding,stop_timezone,vehicle_type,platform_code".split(","),
            "stop_times.txt", "trip_id,stop_id,arrival_time,departure_time,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled".split(","),
            "transfers.txt", "from_stop_id,from_trip_id,to_stop_id,to_trip_id,transfer_type".split(","),
            "trips.txt", "route_id,trip_id,service_id,trip_headsign,direction_id,shape_id,wheelchair_accessible".split(",")
    );
}
