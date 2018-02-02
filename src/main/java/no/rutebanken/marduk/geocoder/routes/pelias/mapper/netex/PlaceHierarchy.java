/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

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
