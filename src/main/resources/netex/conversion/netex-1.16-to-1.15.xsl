<?xml version="1.0" encoding="UTF-8"?>
<!--
  Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
  the European Commission - subsequent versions of the EUPL (the "Licence");
  You may not use this work except in compliance with the Licence.
  You may obtain a copy of the Licence at:

    https://joinup.ec.europa.eu/software/page/eupl

  Unless required by applicable law or agreed to in writing, software
  distributed under the Licence is distributed on an "AS IS" basis,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the Licence for the specific language governing permissions and
  limitations under the Licence.
-->
<!--
  Converts a NeTEx 1.16 PublicationDelivery into a NeTEx 1.15 PublicationDelivery.

  NeTEx 1.16 changed DatedServiceJourney in a way that is not backward compatible:
    * the journey reference is no longer repeatable; replaced journeys moved from
      additional DatedServiceJourneyRef elements into a replacedJourneys container
      holding DatedVehicleJourneyRef / NormalDatedVehicleJourneyRef elements;
    * DatedServiceJourneyRef does not exist any more in the schema;
    * OperatingDayRef and UicOperatingPeriod are no longer mutually exclusive.

  This stylesheet reverses those changes and rewrites the version attribute of the
  PublicationDelivery element. Everything else is copied unchanged.

  Only XSLT 1.0 features are used so the stylesheet runs on the JDK built-in engine
  as well as on libxslt (xmlstarlet tr netex-1.16-to-1.15.xsl input.xml).
-->
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:netex="http://www.netex.org.uk/netex"
                xmlns="http://www.netex.org.uk/netex"
                exclude-result-prefixes="netex">

  <xsl:output method="xml" encoding="UTF-8"/>

  <!-- NeTEx version written in the first component of PublicationDelivery/@version -->
  <xsl:param name="targetVersion" select="'1.15'"/>
  <!-- Nordic profile version written in the last component of PublicationDelivery/@version -->
  <xsl:param name="targetProfileVersion" select="'1.5'"/>

  <!-- Identity transform -->
  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <!--
    PublicationDelivery/@version: "1.16:NO-NeTEx-networktimetable:1.6"
    -> "1.15:NO-NeTEx-networktimetable:1.5"
    The NeTEx version and the Nordic profile version are both rewritten, the profile name in
    between is kept. Every version newer than the target version is rewritten, so a patch version
    such as "1.16.1" is handled as well. A version that already is the target version or an older
    one, and a version without a recognizable version number, are left as is.
  -->
  <xsl:template match="/netex:PublicationDelivery/@version">
    <xsl:variable name="declaredVersion" select="substring-before(concat(., ':'), ':')"/>
    <xsl:variable name="declaredKey">
      <xsl:call-template name="netexVersionKey">
        <xsl:with-param name="version" select="$declaredVersion"/>
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="targetKey">
      <xsl:call-template name="netexVersionKey">
        <xsl:with-param name="version" select="$targetVersion"/>
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="profile" select="substring-after(., ':')"/>
    <xsl:attribute name="version">
      <xsl:choose>
        <xsl:when test="not(number($declaredKey) > number($targetKey))">
          <xsl:value-of select="."/>
        </xsl:when>
        <xsl:when test="contains($profile, ':')">
          <xsl:value-of select="concat($targetVersion, ':', substring-before($profile, ':'), ':', $targetProfileVersion)"/>
        </xsl:when>
        <xsl:when test="$profile != ''">
          <xsl:value-of select="concat($targetVersion, ':', $profile)"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="$targetVersion"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:attribute>
  </xsl:template>

  <!--
    Numeric ordering key of a NeTEx version number ("1.15", "1.16", "1.16.1"), so that the version declared
    by a document can be compared with the target version of this stylesheet. The key is
    major * 1000 + minor; non-digit characters are ignored and anything after the minor number is dropped.
    A version with no recognizable major number yields NaN, which compares false either way and therefore
    leaves the version attribute untouched.
  -->
  <xsl:template name="netexVersionKey">
    <xsl:param name="version"/>
    <xsl:variable name="major" select="substring-before(concat($version, '.'), '.')"/>
    <xsl:variable name="minor" select="substring-before(concat(substring-after($version, '.'), '.'), '.')"/>
    <xsl:variable name="majorDigits" select="translate($major, translate($major, '0123456789', ''), '')"/>
    <xsl:variable name="minorDigits" select="translate($minor, translate($minor, '0123456789', ''), '')"/>
    <xsl:value-of select="number($majorDigits) * 1000 + number(concat('0', $minorDigits))"/>
  </xsl:template>

  <!--
    DatedServiceJourney that needs rewriting (has replacedJourneys, or both OperatingDayRef and
    UicOperatingPeriod): rebuild the tail of the element in the 1.15 order
      JourneyRef*, (OperatingDayRef | UicOperatingPeriod)?, ExternalDatedVehicleJourneyRef?,
      DatedJourneyPatternRef?, DriverRef?
    The leading part of the element (ServiceAlteration, ServiceJourneyRef, ...) is identical
    in both versions and is copied in document order. The children are reordered, so the formatting
    whitespace of the source is dropped and written again by the indented mode below. Other
    DatedServiceJourney elements are copied unchanged by the identity template.
  -->
  <xsl:template match="netex:DatedServiceJourney[netex:replacedJourneys
                                                 or (netex:OperatingDayRef and netex:UicOperatingPeriod)]">
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <xsl:apply-templates mode="indented"
                           select="node()[not(self::netex:replacedJourneys
                                            or self::netex:OperatingDayRef
                                            or self::netex:UicOperatingPeriod
                                            or self::netex:ExternalDatedVehicleJourneyRef
                                            or self::netex:DatedJourneyPatternRef
                                            or self::netex:DriverRef
                                            or (self::text() and normalize-space(.) = ''))]"/>
      <xsl:apply-templates select="netex:replacedJourneys/*" mode="indented"/>
      <xsl:choose>
        <xsl:when test="netex:OperatingDayRef">
          <xsl:apply-templates select="netex:OperatingDayRef" mode="indented"/>
        </xsl:when>
        <xsl:otherwise>
          <!-- UicOperatingPeriod is deprecated in 1.16; only kept when there is no OperatingDayRef -->
          <xsl:apply-templates select="netex:UicOperatingPeriod" mode="indented"/>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:apply-templates select="netex:ExternalDatedVehicleJourneyRef" mode="indented"/>
      <xsl:apply-templates select="netex:DatedJourneyPatternRef" mode="indented"/>
      <xsl:apply-templates select="netex:DriverRef" mode="indented"/>
      <!-- whitespace before the closing tag of the rebuilt element -->
      <xsl:value-of select="text()[normalize-space(.) = ''][last()]"/>
    </xsl:copy>
  </xsl:template>

  <!--
    Copies a node of a rebuilt DatedServiceJourney preceded by the indentation of that element, so that
    the reordered children keep the formatting of the source document. A DatedServiceJourney written on
    a single line has no such whitespace and stays on a single line.
  -->
  <xsl:template match="node()" mode="indented">
    <xsl:value-of select="ancestor::netex:DatedServiceJourney[1]/text()[normalize-space(.) = ''][1]"/>
    <xsl:apply-templates select="."/>
  </xsl:template>

  <!-- replacedJourneys/DatedVehicleJourneyRef and NormalDatedVehicleJourneyRef -> DatedServiceJourneyRef -->
  <xsl:template match="netex:DatedServiceJourney/netex:replacedJourneys/*">
    <DatedServiceJourneyRef>
      <xsl:apply-templates select="@*|node()"/>
    </DatedServiceJourneyRef>
  </xsl:template>

</xsl:stylesheet>
