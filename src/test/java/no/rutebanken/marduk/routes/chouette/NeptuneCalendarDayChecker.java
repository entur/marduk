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

package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.routes.file.beans.FileClassifierPredicates;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

public class NeptuneCalendarDayChecker {

    private final String path = "/home/tomgag/Downloads/export_neptune_2504.zip";

    @Ignore
    @Test
    public void test() throws Exception {
       assertTrue(FileClassifierPredicates.validateZipContent(
                new FileInputStream(path), atLeastOneCalendarDayisAfter(), "metadata_chouette.txt|metadata_chouette_dc.xml"));
    }

    public static Predicate<InputStream> atLeastOneCalendarDayisAfter() {
        return inputStream -> atLeastOneCalendarDayisAfter(LocalDate.now().minusDays(2L)).test(inputStream);
    }

    public static Predicate<InputStream> atLeastOneCalendarDayisAfter(LocalDate localDate) {
        return inputStream -> getCalendarDays(inputStream).anyMatch(ld -> ld.isAfter(localDate));
    }

    private static Stream<LocalDate> getCalendarDays(InputStream inputStream) {
        List<LocalDate> result = new ArrayList<>();
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(inputStream);

                XPath xpath = XPathFactory.newInstance().newXPath();
                String expression = "//calendarDay";

                XPathExpression expr =
                        xpath.compile(expression);

                NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

                for (int i = 0 ; i < nodes.getLength() ; i++ ){
                    String text = nodes.item(i).getTextContent();
                    result.add(LocalDate.parse(text, DateTimeFormatter.ISO_DATE));
                }
                nodes.item(0).getNodeValue();
            } catch (Exception e) {
                System.out.println(e);
            }
        return result.stream();
    }

}