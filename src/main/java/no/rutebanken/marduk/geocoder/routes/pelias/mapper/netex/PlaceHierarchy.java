package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;

import org.rutebanken.netex.model.Place_VersionStructure;

import java.util.ArrayList;
import java.util.Collection;

public class PlaceHierarchy<T extends Place_VersionStructure> {

    private T place;

    private Collection<PlaceHierarchy<T>> children;


    public PlaceHierarchy(T place, Collection<PlaceHierarchy<T>> children) {
        this.place = place;
        this.children = children;
    }

    public PlaceHierarchy(T place) {
        this(place, new ArrayList<>());
    }


    public T getPlace() {
        return place;
    }

    public Collection<PlaceHierarchy<T>> getChildren() {
        return children;
    }
}
