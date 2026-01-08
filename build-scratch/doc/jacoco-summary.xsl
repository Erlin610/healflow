<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="text" encoding="UTF-8"/>

  <xsl:template match="/report">
    <xsl:variable name="missed" select="counter[@type='LINE']/@missed"/>
    <xsl:variable name="covered" select="counter[@type='LINE']/@covered"/>
    <xsl:variable name="total" select="$missed + $covered"/>
    <xsl:variable name="pct" select="100 * $covered div $total"/>
    Total LINE coverage: <xsl:value-of select="format-number($pct,'0.00')"/>% (<xsl:value-of select="$covered"/>/<xsl:value-of select="$total"/>)
  </xsl:template>
</xsl:stylesheet>

