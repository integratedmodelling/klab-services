# Producer clarification draft — NOT SENT

Proposed recipient: Alba Marquez Torres, corresponding-contact lead publicly
listed with PLOS ONE DOI `10.1371/journal.pone.0281348`.
Publication-listed email: `alba.marquez@bc3research.org`.
This is not confirmation of current affiliation/deliverability. No sender identity
or delivery channel has been selected. Sending requires separate authorization.

**Subject:** Clarifying PaL Radiation inputs for a bounded reproduction

Hello Alba,

We are assessing a small, explicitly adapted reproduction of PaL's `rgc_max`,
`rg2` and `fr` models on current k.LAB. We have not claimed scientific replication
or changed the equations. Could you clarify these points, or direct us to the
appropriate data producer?

1. We found `im-alice-spain__spain_solar_radiation_may_2018` on the production
   GeoServer; the archived development URL no longer serves coverage. Is this the
   original product/version? What are its physical units, scale and temporal
   support—especially daily mean during May versus a monthly-integrated value?
   WCS advertises radiance units while S3A specifies MJ/m² and the archived data
   model says MJ. Is there authoritative metadata or a source/checksum manifest?
2. Should the threshold 25 be interpreted on that same time/area support? Is the
   named PAR threshold directly comparable to the incident solar input, as the
   archived equations do, or is a conversion intended?
3. What do the vegetation legend entries represent? In particular, S3B labels
   “FestucaRubra (Agrostis curtisii)”, while the recovered legacy ontology links
   FestucaRubra to a Festuca rubra GBIF key. Are these functional legend labels,
   proxy species, or intended literal taxa? Should genus entries such as Calluna
   and Helictotrichon remain genera despite species names in the supplement?
4. Could you specify the original May 2018 target grid, continuous/categorical
   resampling and nodata behavior, and provide a small independent `rg2`/`fr`
   export including matching and differing main/overstory classes? For reference,
   our candidate mixed-class point is lon -3.6670247765, lat 43.4852776593 (raw
   main code 21, overstory 7); we have not computed a result there.
5. Does the article's stated AGPL data licence cover these currently served
   production inputs, and may a minimal reference crop be shared with attribution
   if separately approved for publication?

We can provide exact archive/resource identifiers, hashes and request details.
Our aim is to preserve original scientific meanings, not silently substitute
inputs or reinterpret taxonomy. Thank you.

---

Review before sending: choose the actual sender/account, confirm the recipient
lead, approve the wording and decide whether any evidence attachments may be
shared. No third-party raster or private environment/credential attachment is
included in this draft. An API request or this document's creation is not outreach.
