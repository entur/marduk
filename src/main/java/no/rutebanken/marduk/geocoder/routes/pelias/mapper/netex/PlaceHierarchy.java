package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;

import org.rutebanken.netex.model.Place_VersionStructure;

import java.util.ArrayList;
import java.util.Collection;

public class PlaceHierarchy<T extends Place_VersionStructure> {

    private PlaceHierarchy<T> parent;

    private T place;

    private Collection<PlaceHierarchy<T>> children;


    public PlaceHierarchy(T place, PlaceHierarchy<T> parent) {
        this.place = place;
        this.parent = parent;
    }

    public PlaceHierarchy(T place) {
        this(place, null);
    }


    public T getPlace() {
        return place;
    }

    public Collection<PlaceHierarchy<T>> getChildren() {
        return children;
    }

    public PlaceHierarchy<T> getParent() {
        return parent;
    }

    public void setChildren(Collection<PlaceHierarchy<T>> children) {
        this.children = children;
    }
}
