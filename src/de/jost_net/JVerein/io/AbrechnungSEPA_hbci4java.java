package de.jost_net.JVerein.io;

// Datum          : 20220803
// Modifziert von : Stefan B�rger
// Funktion       : Anpassung Abrechungslauf Ausgabe DATEI (kein Hibiscus)
//   Diese Klasse nutzt die hbci4java Komponente aus hibiscus. 
//   Abrechnungsl�ufe die die Ausgabe DATEI nutzen, k�nnen somit die aktuellen SEPA-XML Schematas verwenden
//            
// -------------------------------------------------
// Aenderungshistorie:
// 20220804 : sbuer: Erstes Release

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.String;

import org.apache.commons.lang.StringUtils;
import org.kapott.hbci.GV.SepaUtil;
import org.kapott.hbci.GV.generators.ISEPAGenerator;
import org.kapott.hbci.GV.generators.SEPAGeneratorFactory;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.sepa.SepaVersion;
import org.kapott.hbci.sepa.SepaVersion.Type;

import com.itextpdf.text.DocumentException;

import de.jost_net.JVerein.Einstellungen;
import de.jost_net.JVerein.Variable.AbrechnungsParameterMap;
import de.jost_net.JVerein.Variable.AllgemeineMap;
import de.jost_net.JVerein.Variable.MitgliedMap;
import de.jost_net.JVerein.io.Adressbuch.Adressaufbereitung;
import de.jost_net.JVerein.keys.Abrechnungsausgabe;
import de.jost_net.JVerein.keys.Abrechnungsmodi;
import de.jost_net.JVerein.keys.ArtBeitragsart;
import de.jost_net.JVerein.keys.Beitragsmodel;
import de.jost_net.JVerein.keys.IntervallZusatzzahlung;
import de.jost_net.JVerein.keys.Zahlungsrhythmus;
import de.jost_net.JVerein.keys.Zahlungsweg;
import de.jost_net.JVerein.rmi.Abrechnungslauf;
import de.jost_net.JVerein.rmi.Beitragsgruppe;
import de.jost_net.JVerein.rmi.Buchung;
import de.jost_net.JVerein.rmi.Buchungsart;
import de.jost_net.JVerein.rmi.Konto;
import de.jost_net.JVerein.rmi.Kursteilnehmer;
import de.jost_net.JVerein.rmi.Lastschrift;
import de.jost_net.JVerein.rmi.Mitglied;
import de.jost_net.JVerein.rmi.Mitgliedskonto;
import de.jost_net.JVerein.rmi.SekundaereBeitragsgruppe;
import de.jost_net.JVerein.rmi.Zusatzbetrag;
import de.jost_net.JVerein.rmi.ZusatzbetragAbrechnungslauf;
import de.jost_net.JVerein.server.MitgliedUtils;
import de.jost_net.JVerein.util.Datum;
import de.jost_net.JVerein.util.JVDateFormatDATETIME;
import de.jost_net.OBanToo.SEPA.BIC;
import de.jost_net.OBanToo.SEPA.IBAN;
import de.jost_net.OBanToo.SEPA.SEPAException;
import de.jost_net.OBanToo.SEPA.Basislastschrift.Basislastschrift;
import de.jost_net.OBanToo.SEPA.Basislastschrift.Basislastschrift2Pdf;
import de.jost_net.OBanToo.SEPA.Basislastschrift.MandatSequence;
import de.jost_net.OBanToo.SEPA.Basislastschrift.Zahler;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.internal.action.Program;
import de.willuhn.jameica.hbci.HBCIProperties;
import de.willuhn.jameica.hbci.gui.dialogs.PainVersionDialog;
import de.willuhn.jameica.hbci.io.SepaLastschriftMerger;
import de.willuhn.jameica.hbci.rmi.SepaLastSequenceType;
import de.willuhn.jameica.hbci.rmi.SepaLastType;
import de.willuhn.jameica.hbci.rmi.SepaLastschrift;
import de.willuhn.jameica.hbci.rmi.SepaSammelLastschrift;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.ProgressMonitor;

public class AbrechnungSEPA_hbci4java {
	
	private Calendar sepagueltigkeit;
    
	private int counter = 0;
	private int counter_all = 0;
	private int counter_frst = 0;
	private int counter_rcur = 0;
	private int counter_index = 0;
		
    // Initialisierung der lastschrift properties FRST und RCUR
	Properties lastschriftFRST = new Properties();
	Properties lastschriftRCUR = new Properties();
    // Datumsformatierung fuer Lastschrift
	DateFormat ISO_DATE = new SimpleDateFormat(SepaUtil.DATE_FORMAT);
    // GlauebigerID fuer Lastschrift
	String creditorid = Einstellungen.getEinstellung().getGlaeubigerID();
	
	  // Funktion um Property LastschriftFRST und LastschriftRCUR zu fuellen
	  private void lastschrift_fillup(JVereinZahler zahler) throws Exception {
		 String betrag = zahler.getBetrag().toString();
         if (zahler.getMandatsequence().toString().equals("Folgelastschrift")) {
        	    // System.out.println("rcur: counter: " + counter_rcur + ", name : " + zahler.getName() + ", wert : " + zahler.getBetrag());
                // Jetzt die Daten vom Zahler in das LastschriftRCUR Property schreiben
     	 	    counter_rcur++;
     	 	    counter_index=counter_rcur-1;
                lastschriftRCUR.setProperty(SepaUtil.insertIndex("dst.bic",counter_index),      StringUtils.trimToEmpty(zahler.getBic()));
                lastschriftRCUR.setProperty(SepaUtil.insertIndex("dst.iban",counter_index),     StringUtils.trimToEmpty(zahler.getIban()));
                lastschriftRCUR.setProperty(SepaUtil.insertIndex("dst.name",counter_index),     StringUtils.trimToEmpty(zahler.getName()));
                lastschriftRCUR.setProperty(SepaUtil.insertIndex("btg.value",counter_index),    betrag);
                lastschriftRCUR.setProperty(SepaUtil.insertIndex("btg.curr",counter_index),     HBCIProperties.CURRENCY_DEFAULT_DE);
                lastschriftRCUR.setProperty(SepaUtil.insertIndex("usage",counter_index),        StringUtils.trimToEmpty(zahler.getVerwendungszweck()));
                lastschriftRCUR.setProperty(SepaUtil.insertIndex("endtoendid",counter_index),   "NOTPROVIDED");        
                lastschriftRCUR.setProperty(SepaUtil.insertIndex("creditorid",counter_index),   creditorid);
                lastschriftRCUR.setProperty(SepaUtil.insertIndex("mandateid",counter_index),    StringUtils.trimToEmpty(zahler.getMandatid()));
                lastschriftRCUR.setProperty(SepaUtil.insertIndex("manddateofsig",counter_index),ISO_DATE.format(zahler.getMandatdatum()));
                lastschriftRCUR.setProperty(SepaUtil.insertIndex("purposecode",counter_index),  "OHTR");
        	} else {
        		// System.out.println("frst: counter: " + counter_frst + ", name : " + zahler.getName() + ", wert : " + zahler.getBetrag());
        		// Jetzt die Daten vom Zahler in das LastschriftFRST Property schreiben
        		counter_frst++;
        		counter_index=counter_frst-1;
                lastschriftFRST.setProperty(SepaUtil.insertIndex("dst.bic",counter_index),      StringUtils.trimToEmpty(zahler.getBic()));
                lastschriftFRST.setProperty(SepaUtil.insertIndex("dst.iban",counter_index),     StringUtils.trimToEmpty(zahler.getIban()));
                lastschriftFRST.setProperty(SepaUtil.insertIndex("dst.name",counter_index),     StringUtils.trimToEmpty(zahler.getName()));
                lastschriftFRST.setProperty(SepaUtil.insertIndex("btg.value",counter_index),    betrag);
                lastschriftFRST.setProperty(SepaUtil.insertIndex("btg.curr",counter_index),     HBCIProperties.CURRENCY_DEFAULT_DE);
                lastschriftFRST.setProperty(SepaUtil.insertIndex("usage",counter_index),        StringUtils.trimToEmpty(zahler.getVerwendungszweck()));
                lastschriftFRST.setProperty(SepaUtil.insertIndex("endtoendid",counter_index),   "NOTPROVIDED");        
                lastschriftFRST.setProperty(SepaUtil.insertIndex("creditorid",counter_index),   creditorid);
                lastschriftFRST.setProperty(SepaUtil.insertIndex("mandateid",counter_index),    StringUtils.trimToEmpty(zahler.getMandatid()));
                lastschriftFRST.setProperty(SepaUtil.insertIndex("manddateofsig",counter_index),ISO_DATE.format(zahler.getMandatdatum()));
                lastschriftFRST.setProperty(SepaUtil.insertIndex("purposecode",counter_index),  "OHTR");
        	}
         counter_all++;
	  }

	  // Hauptfunktion f�r die SEPA-Lastschrift mit hbci4java
	  public AbrechnungSEPA_hbci4java(AbrechnungSEPAParam param, ProgressMonitor monitor)
	      throws Exception
	  {
	    if (Einstellungen.getEinstellung().getName() == null
	        || Einstellungen.getEinstellung().getName().length() == 0
	        || Einstellungen.getEinstellung().getIban() == null
	        || Einstellungen.getEinstellung().getIban().length() == 0)
	    {
	      throw new ApplicationException(
	          "Name des Vereins oder Bankverbindung fehlt. Bitte unter Administration|Einstellungen erfassen.");
	    }

	    if (Einstellungen.getEinstellung().getGlaeubigerID() == null
	        || Einstellungen.getEinstellung().getGlaeubigerID().length() == 0)
	    {
	      throw new ApplicationException(
	          "Gl�ubiger-ID fehlt. Gfls. unter https://extranet.bundesbank.de/scp/ oder http://www.oenb.at/idakilz/cid?lang=de beantragen und unter Administration|Einstellungen|Allgemein eintragen.\n"
	              + "Zu Testzwecken kann DE98ZZZ09999999999 eingesetzt werden.");
	    }

	    Abrechnungslauf abrl = getAbrechnungslauf(param);

	    sepagueltigkeit = Calendar.getInstance();
	    sepagueltigkeit.add(Calendar.MONTH, -36);
	    
	    // 20220803: sbuer: Relikt aus obantoo und bleibt erstmal als DUMMY erhalten und wird nicht mehr gefuellt
	    JVereinBasislastschrift lastschrift = new JVereinBasislastschrift();
	    
	    // 20220803: sbuer: Anpassungen f�r hbci4java
	    PainVersionDialog d = new PainVersionDialog(org.kapott.hbci.sepa.SepaVersion.Type.PAIN_008);
	    final SepaVersion version = (SepaVersion) d.open();
	    
	    // Epochtime als sepaid und pmtinfid
	    Long epochtime = Calendar.getInstance().getTimeInMillis();
	    String epochtime_string = epochtime.toString();
	    
	    // Initialdaten f�r die Lastschriften FRST und RCUR
		lastschriftFRST.setProperty("src.bic", StringUtils.trimToEmpty(Einstellungen.getEinstellung().getBic()));
		lastschriftFRST.setProperty("src.iban", StringUtils.trimToEmpty(Einstellungen.getEinstellung().getIban()));
		lastschriftFRST.setProperty("src.name", StringUtils.trimToEmpty(Einstellungen.getEinstellung().getName()));
		lastschriftFRST.setProperty("sepaid", epochtime_string);
		lastschriftFRST.setProperty("pmtinfid", epochtime_string);
	    lastschriftFRST.setProperty("sequencetype", "FRST");
	    lastschriftFRST.setProperty("targetdate", abrl.getStichtag() != null ? ISO_DATE.format(abrl.getStichtag()) : SepaUtil.DATE_UNDEFINED);
	    lastschriftFRST.setProperty("type", "CORE");
	    lastschriftFRST.setProperty("batchbook", "");
	  
		lastschriftRCUR.setProperty("src.bic", StringUtils.trimToEmpty(Einstellungen.getEinstellung().getBic()));
		lastschriftRCUR.setProperty("src.iban", StringUtils.trimToEmpty(Einstellungen.getEinstellung().getIban()));
		lastschriftRCUR.setProperty("src.name", StringUtils.trimToEmpty(Einstellungen.getEinstellung().getName()));
		lastschriftRCUR.setProperty("sepaid", epochtime_string);
		lastschriftRCUR.setProperty("pmtinfid", epochtime_string);
		lastschriftRCUR.setProperty("sequencetype", "RCUR");
	    lastschriftRCUR.setProperty("targetdate", abrl.getStichtag() != null ? ISO_DATE.format(abrl.getStichtag()) : SepaUtil.DATE_UNDEFINED);
	    lastschriftRCUR.setProperty("type", "CORE");
	    lastschriftRCUR.setProperty("batchbook", "");
	    
	    // Konto ermitteln
	    Konto konto = getKonto();
	    
	    // In diesen Methoden werden nun die Lastschriften mit der Methode lastschrift_fillup gefuellt
	    abrechnenMitglieder(param, lastschrift, monitor, abrl, konto);
	    if (param.zusatzbetraege)
	    {
	      abbuchenZusatzbetraege(param, lastschrift, abrl, konto, monitor);
	    }
	    if (param.kursteilnehmer)
	    {
	      abbuchenKursteilnehmer(param, lastschrift);
	    }

	    monitor.log(counter_all + " abgerechnete F�lle, davon " + counter_frst + " FRST, " + counter_rcur + " RCUR");
	    // Lastschrift Datei RCUR erzeugen: Aber nur wenn auch welche gezaehlt wurden
	    if ( counter_frst > 0 ) {
	    	final OutputStream os = new FileOutputStream(param.sepafileFRST);
		    System.setProperty("sepa.pain.formatted","true");
		    ISEPAGenerator sepagenerator = SEPAGeneratorFactory.get("LastSEPA",version);
		    try
		    {
			    sepagenerator.generate(lastschriftFRST,os,true);
		    }
	        catch (IOException e)
	        {
	          Logger.error("Generieren der SEPA-XML FRST fehlgeschlagen!", e);
	        }
		    Logger.info("Genieren der SEPA-XML FRST erfolgreich : " + param.sepafileFRST);
		    monitor.log("Genieren der SEPA-XML FRST erfolgreich : " + param.sepafileFRST);
	    }
	    
	    // Lastschrift Datei RCUR erzeugen: Aber nur wenn auch welche gezaehlt wurden	    
	    if ( counter_rcur > 0 ) {
	    	final OutputStream os = new FileOutputStream(param.sepafileRCUR);
		    System.setProperty("sepa.pain.formatted","true");
		    ISEPAGenerator sepagenerator = SEPAGeneratorFactory.get("LastSEPA",version);
		    try
		    {
			    sepagenerator.generate(lastschriftRCUR,os,true);
		    }
	        catch (IOException e)
	        {
	          Logger.error("Generieren der SEPA-XML RCUR fehlgeschlagen!", e);
	        }
		    Logger.info("Genieren der SEPA-XML RCUR erfolgreich : " + param.sepafileRCUR);
		    monitor.log("Genieren der SEPA-XML RCUR erfolgreich : " + param.sepafileRCUR);
	    }	    
	   

	    ArrayList<Zahler> z = lastschrift.getZahler();
	    BigDecimal summemitgliedskonto = new BigDecimal("0");
	    for (Zahler za : z)
	    {
	      Lastschrift ls = (Lastschrift) Einstellungen.getDBService()
	          .createObject(Lastschrift.class, null);
	      ls.setAbrechnungslauf(Integer.parseInt(abrl.getID()));

	      assert (za instanceof JVereinZahler) : "Illegaler Zahlertyp in Sepa-Abrechnung detektiert.";

	      JVereinZahler vza = (JVereinZahler) za;

	      switch (vza.getPersonTyp())
	      {
	        case KURSTEILNEHMER:
	          ls.setKursteilnehmer(Integer.parseInt(vza.getPersonId()));
	          Kursteilnehmer k = (Kursteilnehmer) Einstellungen.getDBService()
	              .createObject(Kursteilnehmer.class, vza.getPersonId());
	          ls.setPersonenart(k.getPersonenart());
	          ls.setAnrede(k.getAnrede());
	          ls.setTitel(k.getTitel());
	          ls.setName(k.getName());
	          ls.setVorname(k.getVorname());
	          ls.setStrasse(k.getStrasse());
	          ls.setAdressierungszusatz(k.getAdressierungszusatz());
	          ls.setPlz(k.getPlz());
	          ls.setOrt(k.getOrt());
	          ls.setStaat(k.getStaat());
	          ls.setEmail(k.getEmail());
	          break;
	        case MITGLIED:
	          ls.setMitglied(Integer.parseInt(vza.getPersonId()));
	          Mitglied m = (Mitglied) Einstellungen.getDBService()
	              .createObject(Mitglied.class, vza.getPersonId());
	          if (m.getKtoiName() == null || m.getKtoiName().length() == 0)
	          {
	            ls.setPersonenart(m.getPersonenart());
	            ls.setAnrede(m.getAnrede());
	            ls.setTitel(m.getTitel());
	            ls.setName(m.getName());
	            ls.setVorname(m.getVorname());
	            ls.setStrasse(m.getStrasse());
	            ls.setAdressierungszusatz(m.getAdressierungszusatz());
	            ls.setPlz(m.getPlz());
	            ls.setOrt(m.getOrt());
	            ls.setStaat(m.getStaat());
	            ls.setEmail(m.getEmail());
	            ls.setGeschlecht(m.getGeschlecht());
	          }
	          else
	          {
	            ls.setPersonenart(m.getKtoiPersonenart());
	            ls.setAnrede(m.getKtoiAnrede());
	            ls.setTitel(m.getKtoiTitel());
	            ls.setName(m.getKtoiName());
	            ls.setVorname(m.getKtoiVorname());
	            ls.setStrasse(m.getKtoiStrasse());
	            ls.setAdressierungszusatz(m.getKtoiAdressierungszusatz());
	            ls.setPlz(m.getKtoiPlz());
	            ls.setOrt(m.getKtoiOrt());
	            ls.setStaat(m.getKtoiStaat());
	            ls.setEmail(m.getKtoiEmail());
	            ls.setGeschlecht(m.getKtoiGeschlecht());
	          }
	          break;
	        default:
	          assert false : "Personentyp ist nicht implementiert";
	      }
	      ls.setBetrag(za.getBetrag().doubleValue());
	      summemitgliedskonto = summemitgliedskonto.add(za.getBetrag());
	      ls.setBIC(za.getBic());
	      ls.setIBAN(za.getIban());
	      ls.setMandatDatum(za.getMandatdatum());
	      ls.setMandatSequence(za.getMandatsequence().getTxt());
	      ls.setMandatID(za.getMandatid());
	      ls.setVerwendungszweck(za.getVerwendungszweck());
	      ls.store();
	    }

	    // Gegenbuchung f�r das Mitgliedskonto schreiben
	    if (!summemitgliedskonto.equals(new BigDecimal("0")))
	    {
	      writeMitgliedskonto(null, new Date(), "Gegenbuchung",
	          summemitgliedskonto.doubleValue() * -1, abrl, true, getKonto(), null);
	    }
	    if (param.abbuchungsausgabe == Abrechnungsausgabe.HIBISCUS)
	    {
	      buchenHibiscus(param, z);
	    }
	    monitor.setPercentComplete(100);
	    if (param.sepaprint)
	    {
	      ausdruckenSEPA(lastschrift, param.pdffileFRST, param.pdffileRCUR);
	    }
	  }

	  private void abrechnenMitglieder(AbrechnungSEPAParam param,
	      JVereinBasislastschrift lastschrift, ProgressMonitor monitor,
	      Abrechnungslauf abrl, Konto konto) throws Exception
	  {
	    if (param.abbuchungsmodus != Abrechnungsmodi.KEINBEITRAG)
	    {
	      // Alle Mitglieder lesen
	      DBIterator<Mitglied> list = Einstellungen.getDBService()
	          .createList(Mitglied.class);
	      MitgliedUtils.setMitglied(list);

	      // Das Mitglied muss bereits eingetreten sein
	      list.addFilter("(eintritt <= ? or eintritt is null) ", new Object[] { new java.sql.Date(param.stichtag.getTime()) });
	      // Das Mitglied darf noch nicht ausgetreten sein
	      list.addFilter("(austritt is null or austritt > ?)", new Object[] { new java.sql.Date(param.stichtag.getTime()) });
	      // Bei Abbuchungen im Laufe des Jahres werden nur die Mitglieder
	      // ber�cksichtigt, die bis zu einem bestimmten Zeitpunkt ausgetreten sind.
	      if (param.bisdatum != null)
	      {
	        list.addFilter("(austritt <= ?)", new Object[] { new java.sql.Date(param.bisdatum.getTime()) });
	      }
	      // Bei Abbuchungen im Laufe des Jahres werden nur die Mitglieder
	      // ber�cksichtigt, die ab einem bestimmten Zeitpunkt eingetreten sind.
	      if (param.vondatum != null)
	      {
	        list.addFilter("eingabedatum >= ?", new Object[] { new java.sql.Date(param.vondatum.getTime()) });
	      }
	      if (Einstellungen.getEinstellung()
	          .getBeitragsmodel() == Beitragsmodel.MONATLICH12631)
	      {
	        if (param.abbuchungsmodus == Abrechnungsmodi.HAVIMO)
	        {
	          list.addFilter(
	              "(zahlungsrhytmus = ? or zahlungsrhytmus = ? or zahlungsrhytmus = ?)",
	                  new Object[] { new Integer(Zahlungsrhythmus.HALBJAEHRLICH),
	                  new Integer(Zahlungsrhythmus.VIERTELJAEHRLICH),
	                  new Integer(Zahlungsrhythmus.MONATLICH) });
	        }
	        if (param.abbuchungsmodus == Abrechnungsmodi.JAVIMO)
	        {
	          list.addFilter(
	              "(zahlungsrhytmus = ? or zahlungsrhytmus = ? or zahlungsrhytmus = ?)",
	                  new Object[] { new Integer(Zahlungsrhythmus.JAEHRLICH),
	                  new Integer(Zahlungsrhythmus.VIERTELJAEHRLICH),
	                  new Integer(Zahlungsrhythmus.MONATLICH) });
	        }
	        if (param.abbuchungsmodus == Abrechnungsmodi.VIMO)
	        {
	          list.addFilter("(zahlungsrhytmus = ? or zahlungsrhytmus = ?)",
	                  new Object[] { Integer.valueOf(Zahlungsrhythmus.VIERTELJAEHRLICH),
	                  Integer.valueOf(Zahlungsrhythmus.MONATLICH) });
	        }
	        if (param.abbuchungsmodus == Abrechnungsmodi.MO)
	        {
	          list.addFilter("zahlungsrhytmus = ?",
	                  new Object[] { Integer.valueOf(Zahlungsrhythmus.MONATLICH) });
	        }
	        if (param.abbuchungsmodus == Abrechnungsmodi.VI)
	        {
	          list.addFilter("zahlungsrhytmus = ?", new Object[] {
	              Integer.valueOf(Zahlungsrhythmus.VIERTELJAEHRLICH) });
	        }
	        if (param.abbuchungsmodus == Abrechnungsmodi.HA)
	        {
	          list.addFilter("zahlungsrhytmus = ?",
	              new Object[] { Integer.valueOf(Zahlungsrhythmus.HALBJAEHRLICH) });
	        }
	        if (param.abbuchungsmodus == Abrechnungsmodi.JA)
	        {
	          list.addFilter("zahlungsrhytmus = ?",
	              new Object[] { Integer.valueOf(Zahlungsrhythmus.JAEHRLICH) });
	        }
	      }

	      list.setOrder("ORDER BY name, vorname");

	      // S�tze im Resultset
	      int count = 0;
	      while (list.hasNext())
	      {
	        monitor.setStatus((int) ((double) count / (double) list.size() * 100d));
	        Mitglied m = (Mitglied) list.next();

	        JVereinZahler z = abrechnungMitgliederSub(param, lastschrift, monitor,
	            abrl, konto, m, m.getBeitragsgruppe(), true);

	        DBIterator<SekundaereBeitragsgruppe> sekundaer = Einstellungen
	            .getDBService().createList(SekundaereBeitragsgruppe.class);
	        sekundaer.addFilter("mitglied=?", m.getID());
	        while (sekundaer.hasNext())
	        {
	          SekundaereBeitragsgruppe sb = (SekundaereBeitragsgruppe) sekundaer
	              .next();
	          JVereinZahler z2 = abrechnungMitgliederSub(param, lastschrift,
	              monitor, abrl, konto, m, sb.getBeitragsgruppe(), false);
	          if (z2 != null)
	          {
	            if (z != null)
	            {
	              z.add(z2);
	            }
	            else
	            {
	              z = z2;
	            }
	          }
	        }
	        // Da alles in z ueberfuehrt wird, mu� jetzt hier geprueft werden ob das lastschrift property gefuellt werden kann
	        if (z != null)
	        {
	        // 20220804: sbuer: Anpasung fur hbci4java
	        // lastschrift.add(z);
	        lastschrift_fillup(z);
	        }
	      }
	    }
	  }

	  private JVereinZahler abrechnungMitgliederSub(AbrechnungSEPAParam param,
	      JVereinBasislastschrift lastschrift, ProgressMonitor monitor,
	      Abrechnungslauf abrl, Konto konto, Mitglied m, Beitragsgruppe bg,
	      boolean primaer) throws RemoteException, ApplicationException
	  {
	    Double betr = 0d;
	    JVereinZahler zahler = null;
	    if (Einstellungen.getEinstellung()
	        .getBeitragsmodel() == Beitragsmodel.FLEXIBEL)
	    {
	      if (m.getZahlungstermin() != null
	          && !m.getZahlungstermin().isAbzurechnen(param.abrechnungsmonat))
	      {
	        return zahler;
	      }
	    }

	    try
	    {
	      betr = BeitragsUtil.getBeitrag(
	          Einstellungen.getEinstellung().getBeitragsmodel(),
	          m.getZahlungstermin(), m.getZahlungsrhythmus().getKey(), bg,
	          param.stichtag, m.getEintritt(), m.getAustritt());
	    }
	    catch (NullPointerException e)
	    {
	      throw new ApplicationException(
	          "Zahlungsinformationen bei " + m.getName() + ", " + m.getVorname());
	    }
	    if (primaer)
	    {
	      if (Einstellungen.getEinstellung().getIndividuelleBeitraege()
	          && m.getIndividuellerBeitrag() > 0)
	      {
	        betr = m.getIndividuellerBeitrag();
	      }
	    }
	    if (betr == 0d)
	    {
	      return zahler;
	    }
	    if (!checkSEPA(m, monitor))
	    {
	      return zahler;
	    }
	    counter++;

	    String vzweck = abrl.getZahlungsgrund();
	    Map<String, Object> map = new MitgliedMap().getMap(m, null);
	    try
	    {
	      vzweck = VelocityTool.eval(map, vzweck);
	    }
	    catch (IOException e)
	    {
	      Logger.error("Fehler bei der Aufbereitung der Variablen", e);
	    }

	    writeMitgliedskonto(m,
	        m.getMandatSequence().getTxt().equals("FRST") ? param.faelligkeit1
	            : param.faelligkeit2,
	        primaer ? vzweck : bg.getBezeichnung(), betr, abrl,
	        m.getZahlungsweg() == Zahlungsweg.BASISLASTSCHRIFT, konto,
	        bg.getBuchungsart());
	    if (m.getZahlungsweg() == Zahlungsweg.BASISLASTSCHRIFT)
	    {
	      try
	      {
	        zahler = new JVereinZahler();
	        zahler.setPersonId(m.getID());
	        zahler.setPersonTyp(JVereinZahlerTyp.MITGLIED);
	        zahler.setBetrag(
	            new BigDecimal(betr).setScale(2, BigDecimal.ROUND_HALF_UP));
	        new BIC(m.getBic()); // Pr�fung des BIC
	        zahler.setBic(m.getBic());
	        new IBAN(m.getIban()); // Pr�fung der IBAN
	        zahler.setIban(m.getIban());
	        zahler.setMandatid(m.getMandatID());
	        zahler.setMandatdatum(m.getMandatDatum());
	        zahler.setMandatsequence(m.getMandatSequence());
	        zahler.setFaelligkeit(param.faelligkeit1, param.faelligkeit2,
	            m.getMandatSequence().getCode());
	        if (primaer)
	        {
	          zahler.setVerwendungszweck(getVerwendungszweck2(m) + " " + vzweck);
	        }
	        else
	        {
	          zahler.setVerwendungszweck(bg.getBezeichnung());
	        }
	        if (m.getBeitragsgruppe()
	            .getBeitragsArt() == ArtBeitragsart.FAMILIE_ZAHLER)
	        {
	          DBIterator<Mitglied> angeh = Einstellungen.getDBService()
	              .createList(Mitglied.class);
	          angeh.addFilter("zahlerid = ?", m.getID());
	          String an = "";
	          int i = 0;
	          while (angeh.hasNext())
	          {
	            Mitglied a = (Mitglied) angeh.next();
	            if (i > 0)
	            {
	              an += ", ";
	            }
	            i++;
	            an += a.getVorname();
	          }
	          zahler.setVerwendungszweck(zahler.getVerwendungszweck() + " " + an);
	        }
	        zahler.setName(m.getKontoinhaber(1));
	      }
	      catch (Exception e)
	      {
	        throw new ApplicationException(
	            Adressaufbereitung.getNameVorname(m) + ": " + e.getMessage());
	      }
	    }
	    return zahler;
	  }

	  private void abbuchenZusatzbetraege(AbrechnungSEPAParam param,
	      JVereinBasislastschrift lastschrift, Abrechnungslauf abrl, Konto konto,
	      ProgressMonitor monitor)
	      throws NumberFormatException, IOException, ApplicationException
	  {
	    DBIterator<Zusatzbetrag> list = Einstellungen.getDBService()
	        .createList(Zusatzbetrag.class);
	    while (list.hasNext())
	    {
	      Zusatzbetrag z = (Zusatzbetrag) list.next();
	      if (z.isAktiv(param.stichtag))
	      {
	        Mitglied m = z.getMitglied();
	        if (m.isAngemeldet(param.stichtag)
	            || Einstellungen.getEinstellung().getZusatzbetragAusgetretene())
	        {
	          //
	        }
	        else
	        {
	          continue;
	        }
	        if (!checkSEPA(m, monitor))
	        {
	          continue;
	        }
	        counter++;
	        String vzweck = z.getBuchungstext();
	        Map<String, Object> map = new AllgemeineMap().getMap(null);
	        map = new MitgliedMap().getMap(m, map);
	        map = new AbrechnungsParameterMap().getMap(param, map);
	        try
	        {
	          vzweck = VelocityTool.eval(map, vzweck);
	        }
	        catch (IOException e)
	        {
	          Logger.error("Fehler bei der Aufbereitung der Variablen", e);
	        }
	        if (m.getZahlungsweg() == Zahlungsweg.BASISLASTSCHRIFT)
	        {
	          try
	          {
	            JVereinZahler zahler = new JVereinZahler();
	            zahler.setPersonId(m.getID());
	            zahler.setPersonTyp(JVereinZahlerTyp.MITGLIED);
	            zahler.setBetrag(new BigDecimal(z.getBetrag()).setScale(2,
	                BigDecimal.ROUND_HALF_UP));
	            new BIC(m.getBic());
	            new IBAN(m.getIban());
	            zahler.setBic(m.getBic());
	            zahler.setIban(m.getIban());
	            zahler.setMandatid(m.getMandatID());
	            zahler.setMandatdatum(m.getMandatDatum());
	            zahler.setMandatsequence(m.getMandatSequence());
	            zahler.setFaelligkeit(param.faelligkeit1, param.faelligkeit2,
	                m.getMandatSequence().getCode());
	            zahler.setName(m.getKontoinhaber(1));
	            zahler.setVerwendungszweck(vzweck);
		        // 20220804: sbuer: Anpasung fur hbci4java
		        // lastschrift.add(zahler);
		        lastschrift_fillup(zahler);
	          }
	          catch (Exception e)
	          {
	            throw new ApplicationException(
	                Adressaufbereitung.getNameVorname(m) + ": " + e.getMessage());
	          }
	        }
	        if (z.getIntervall().intValue() != IntervallZusatzzahlung.KEIN
	            && (z.getEndedatum() == null
	                || z.getFaelligkeit().getTime() <= z.getEndedatum().getTime()))
	        {
	          z.setFaelligkeit(
	              Datum.addInterval(z.getFaelligkeit(), z.getIntervall()));
	        }
	        try
	        {
	          if (abrl != null)
	          {
	            ZusatzbetragAbrechnungslauf za = (ZusatzbetragAbrechnungslauf) Einstellungen
	                .getDBService()
	                .createObject(ZusatzbetragAbrechnungslauf.class, null);
	            za.setAbrechnungslauf(abrl);
	            za.setZusatzbetrag(z);
	            za.setLetzteAusfuehrung(z.getAusfuehrung());
	            za.store();
	            z.setAusfuehrung(Datum.getHeute());
	            z.store();
	          }
	        }
	        catch (ApplicationException e)
	        {
	          String debString = z.getStartdatum() + ", " + z.getEndedatum() + ", "
	              + z.getIntervallText() + ", " + z.getBuchungstext() + ", "
	              + z.getBetrag();
	          Logger.error(Adressaufbereitung.getNameVorname(z.getMitglied()) + " "
	              + debString, e);
	          monitor.log(z.getMitglied().getName() + " " + debString + " " + e);
	          throw e;
	        }
	        writeMitgliedskonto(m,
	            m.getMandatSequence().getTxt().equals("FRST") ? param.faelligkeit1
	                : param.faelligkeit2,
	            vzweck, z.getBetrag(), abrl,
	            m.getZahlungsweg() == Zahlungsweg.BASISLASTSCHRIFT, konto,
	            z.getBuchungsart());
	      }
	    }
	  }

	  private void abbuchenKursteilnehmer(AbrechnungSEPAParam param,
	      JVereinBasislastschrift lastschrift)
	      throws ApplicationException, IOException
	  {
	    DBIterator<Kursteilnehmer> list = Einstellungen.getDBService()
	        .createList(Kursteilnehmer.class);
	    list.addFilter("abbudatum is null");
	    while (list.hasNext())
	    {
	      counter++;
	      Kursteilnehmer kt = (Kursteilnehmer) list.next();
	      try
	      {
	        JVereinZahler zahler = new JVereinZahler();
	        zahler.setPersonId(kt.getID());
	        zahler.setPersonTyp(JVereinZahlerTyp.KURSTEILNEHMER);
	        zahler.setBetrag(new BigDecimal(kt.getBetrag()).setScale(2,
	            BigDecimal.ROUND_HALF_UP));
	        new BIC(kt.getBic());
	        new IBAN(kt.getIban());
	        zahler.setBic(kt.getBic());
	        zahler.setIban(kt.getIban());
	        zahler.setMandatid(kt.getMandatID());
	        zahler.setMandatdatum(kt.getMandatDatum());
	        zahler.setMandatsequence(MandatSequence.FRST);
	        zahler.setFaelligkeit(param.faelligkeit1);
	        zahler.setName(kt.getName());
	        zahler.setVerwendungszweck(kt.getVZweck1());
	        // 20220804: sbuer: Anpasung fur hbci4java
	        // lastschrift.add(zahler);
	        lastschrift_fillup(zahler);
	        kt.setAbbudatum();
	        kt.store();
	      }
	      catch (Exception e)
	      {
	        throw new ApplicationException(kt.getName() + ": " + e.getMessage());
	      }
	    }
	  }

	  private void ausdruckenSEPA(final JVereinBasislastschrift lastschrift,
	      final String pdfFRST, final String pdfRCUR)
	      throws IOException, DocumentException, SEPAException
	  {
	    new Basislastschrift2Pdf(lastschrift.getLastschriftFRST(), pdfFRST);
	    GUI.getDisplay().asyncExec(new Runnable()
	    {

	      @Override
	      public void run()
	      {
	        try
	        {
	          new Program().handleAction(new File(pdfFRST));
	        }
	        catch (ApplicationException ae)
	        {
	          Application.getMessagingFactory().sendMessage(new StatusBarMessage(
	              ae.getLocalizedMessage(), StatusBarMessage.TYPE_ERROR));
	        }
	      }
	    });
	    new Basislastschrift2Pdf(lastschrift.getLastschriftRCUR(), pdfRCUR);
	    GUI.getDisplay().asyncExec(new Runnable()
	    {

	      @Override
	      public void run()
	      {
	        try
	        {
	          new Program().handleAction(new File(pdfRCUR));
	        }
	        catch (ApplicationException ae)
	        {
	          Application.getMessagingFactory().sendMessage(new StatusBarMessage(
	              ae.getLocalizedMessage(), StatusBarMessage.TYPE_ERROR));
	        }
	      }
	    });
	  }

	  private void buchenHibiscus(AbrechnungSEPAParam param, ArrayList<Zahler> z)
	      throws ApplicationException
	  {
	    if (z.size() == 0)
	    {
	      // Wenn keine Buchungen vorhanden sind, wird nichts an Hibiscus �bergeben.
	      return;
	    }
	    try
	    {
	      SepaLastschrift[] lastschriften = new SepaLastschrift[z.size()];
	      int sli = 0;
	      Date d = new Date();
	      for (Zahler za : z)
	      {
	        SepaLastschrift sl = (SepaLastschrift) param.service
	            .createObject(SepaLastschrift.class, null);
	        sl.setBetrag(za.getBetrag().doubleValue());
	        sl.setCreditorId(Einstellungen.getEinstellung().getGlaeubigerID());
	        sl.setGegenkontoName(za.getName());
	        sl.setGegenkontoBLZ(za.getBic());
	        sl.setGegenkontoNummer(za.getIban());
	        sl.setKonto(param.konto);
	        sl.setMandateId(za.getMandatid());
	        sl.setSequenceType(
	            SepaLastSequenceType.valueOf(za.getMandatsequence().getTxt()));
	        sl.setSignatureDate(za.getMandatdatum());
	        sl.setTargetDate(za.getFaelligkeit());
	        sl.setTermin(d);
	        sl.setType(SepaLastType.CORE);
	        sl.setZweck(za.getVerwendungszweck());
	        lastschriften[sli] = sl;
	        sli++;
	      }
	      SepaLastschriftMerger merger = new SepaLastschriftMerger();
	      List<SepaSammelLastschrift> sammler = merger
	          .merge(Arrays.asList(lastschriften));
	      for (SepaSammelLastschrift s : sammler)
	      {
	        // Hier noch die eigene Bezeichnung einfuegen
	        String vzweck = getVerwendungszweck(param) + " "
	            + s.getBezeichnung().substring(0, s.getBezeichnung().indexOf(" "))
	            + " vom " + new JVDateFormatDATETIME().format(new Date());
	        s.setBezeichnung(vzweck);
	        s.store();
	      }
	    }
	    catch (RemoteException e)
	    {
	      throw new ApplicationException(e);
	    }
	    catch (SEPAException e)
	    {
	      throw new ApplicationException(e);
	    }
	  }

	  private String getVerwendungszweck(AbrechnungSEPAParam param)
	      throws RemoteException
	  {
	    Map<String, Object> map = new AllgemeineMap().getMap(null);
	    map = new AbrechnungsParameterMap().getMap(param, map);
	    try
	    {
	      return VelocityTool.eval(map, param.verwendungszweck);
	    }
	    catch (IOException e)
	    {
	      Logger.error("Fehler bei der Aufbereitung der Variablen", e);
	      return param.verwendungszweck;
	    }
	  }

	  private Abrechnungslauf getAbrechnungslauf(AbrechnungSEPAParam param)
	      throws RemoteException, ApplicationException
	  {
	    Abrechnungslauf abrl = (Abrechnungslauf) Einstellungen.getDBService()
	        .createObject(Abrechnungslauf.class, null);
	    abrl.setDatum(new Date());
	    abrl.setAbbuchungsausgabe(param.abbuchungsausgabe.getKey());
	    abrl.setFaelligkeit(param.faelligkeit1);
	    abrl.setFaelligkeit2(param.faelligkeit2);
	    abrl.setDtausdruck(param.sepaprint);
	    abrl.setEingabedatum(param.vondatum);
	    abrl.setAustrittsdatum(param.bisdatum);
	    abrl.setKursteilnehmer(param.kursteilnehmer);
	    abrl.setModus(param.abbuchungsmodus);
	    abrl.setStichtag(param.stichtag);
	    abrl.setZahlungsgrund(getVerwendungszweck(param));
	    abrl.setZusatzbetraege(param.zusatzbetraege);
	    abrl.setAbgeschlossen(false);
	    abrl.store();
	    return abrl;
	  }

	  private void writeMitgliedskonto(Mitglied mitglied, Date datum, String zweck1,
	      double betrag, Abrechnungslauf abrl, boolean haben, Konto konto,
	      Buchungsart buchungsart) throws ApplicationException, RemoteException
	  {
	    Mitgliedskonto mk = null;
	    if (mitglied != null) /*
	                           * Mitglied darf dann null sein, wenn die Gegenbuchung
	                           * geschrieben wird
	                           */
	    {
	      mk = (Mitgliedskonto) Einstellungen.getDBService()
	          .createObject(Mitgliedskonto.class, null);
	      mk.setAbrechnungslauf(abrl);
	      mk.setZahlungsweg(mitglied.getZahlungsweg());
	      mk.setBetrag(betrag);
	      mk.setDatum(datum);
	      mk.setMitglied(mitglied);
	      mk.setZweck1(zweck1);
	      if (buchungsart != null)
	      {
	        mk.setBuchungsart(buchungsart);
	      }
	      mk.store();
	    }
	    if (haben)
	    {
	      Buchung buchung = (Buchung) Einstellungen.getDBService()
	          .createObject(Buchung.class, null);
	      buchung.setAbrechnungslauf(abrl);
	      buchung.setBetrag(betrag);
	      buchung.setDatum(datum);
	      buchung.setKonto(konto);
	      buchung.setName(
	          mitglied != null ? Adressaufbereitung.getNameVorname(mitglied)
	              : "JVerein");
	      buchung.setZweck(zweck1);
	      if (mk != null)
	      {
	        buchung.setMitgliedskonto(mk);
	      }
	      if (buchungsart != null)
	      {
	        buchung.setBuchungsart(new Long(buchungsart.getID()));
	      }
	      buchung.store();
	    }
	  }

	  /**
	   * Ist das Abbuchungskonto in der Buchf�hrung eingerichtet?
	   * 
	   * @throws SEPAException
	   */
	  private Konto getKonto()
	      throws ApplicationException, RemoteException, SEPAException
	  {
	    // Variante 1: IBAN
	    DBIterator<Konto> it = Einstellungen.getDBService().createList(Konto.class);
	    it.addFilter("nummer = ?", Einstellungen.getEinstellung().getIban());
	    if (it.size() == 1)
	    {
	      return (Konto) it.next();
	    }
	    // Variante 2: Kontonummer aus IBAN
	    it = Einstellungen.getDBService().createList(Konto.class);
	    IBAN iban = new IBAN(Einstellungen.getEinstellung().getIban());
	    it.addFilter("nummer = ?", iban.getKonto());
	    if (it.size() == 1)
	    {
	      return (Konto) it.next();
	    }
	    throw new ApplicationException(String.format(
	        "Weder Konto %s noch Konto %s ist in der Buchf�hrung eingerichtet. Menu: Buchf�hrung | Konten",
	        Einstellungen.getEinstellung().getIban(), iban.getKonto()));
	  }

	  private String getVerwendungszweck2(Mitglied m) throws RemoteException
	  {
	    String mitgliedname = (Einstellungen.getEinstellung()
	        .getExterneMitgliedsnummer() ? m.getExterneMitgliedsnummer()
	            : m.getID())
	        + "/" + Adressaufbereitung.getNameVorname(m);
	    return mitgliedname;
	  }

	  private boolean checkSEPA(Mitglied m, ProgressMonitor monitor)
	      throws RemoteException, ApplicationException
	  {
	    if (m.getZahlungsweg() == null
	        || m.getZahlungsweg() != Zahlungsweg.BASISLASTSCHRIFT)
	    {
	      return true;
	    }
	    if (m.getLetzteLastschrift() != null
	        && m.getLetzteLastschrift().before(sepagueltigkeit.getTime()))
	    {
	      monitor.log(Adressaufbereitung.getNameVorname(m)
	          + ": Letzte Lastschrift ist �lter als 36 Monate.");
	      return false;
	    }
	    if (m.getMandatSequence().equals(MandatSequence.FRST)
	        && m.getLetzteLastschrift() != null)
	    {
	      Mitglied m1 = (Mitglied) Einstellungen.getDBService()
	          .createObject(Mitglied.class, m.getID());
	      m1.setMandatSequence(MandatSequence.RCUR);
	      m1.store();
	      m.setMandatSequence(MandatSequence.RCUR);
	    }
	    if (m.getMandatDatum() == Einstellungen.NODATE)
	    {
	      monitor.log(Adressaufbereitung.getNameVorname(m)
	          + ": Kein Mandat-Datum vorhanden.");
	      return false;
	    }
	    return true;
	  }

	}

    // 20220813: sbuer: Diese Klasse ist durch die Kopie der Klasse AbrechnungSEPA entstanden.
    //                  Damit die Klasse nicht doppelt vorhanden ist, wurde sie um den Namen DUMMY erg�nzt
    //                  Die Klasse wird nicht verwendet.
	class JVereinBasislastschrift_DUMMY
	{
	  private Basislastschrift lastschriftFRST;

	  private Basislastschrift lastschriftRCUR;

	  public JVereinBasislastschrift_DUMMY()
	  {
	    lastschriftFRST = new Basislastschrift();
	    lastschriftRCUR = new Basislastschrift();
	  }

	  public void setBIC(String bic) throws SEPAException
	  {
	    lastschriftFRST.setBIC(bic);
	    lastschriftRCUR.setBIC(bic);
	  }

	  public void setGlaeubigerID(String glaeubigerid)
	      throws RemoteException, SEPAException
	  {
	    lastschriftFRST.setGlaeubigerID(glaeubigerid);
	    lastschriftRCUR.setGlaeubigerID(glaeubigerid);
	  }

	  public void setIBAN(String iban) throws SEPAException
	  {
	    lastschriftFRST.setIBAN(iban);
	    lastschriftRCUR.setIBAN(iban);
	  }

	  public void setKomprimiert(boolean kompakt) throws SEPAException
	  {
	    lastschriftFRST.setKomprimiert(kompakt);
	    lastschriftRCUR.setKomprimiert(kompakt);
	  }

	  public void setName(String name) throws SEPAException
	  {
	    lastschriftFRST.setName(name);
	    lastschriftRCUR.setName(name);
	  }

	  public void setMessageID(String id) throws SEPAException
	  {
	    lastschriftFRST.setMessageID(id + "-FRST");
	    lastschriftRCUR.setMessageID(id + "-RCUR");
	  }

	  public void write(File frst, File rcur)
	      throws DatatypeConfigurationException, SEPAException, JAXBException
	  {
	    lastschriftFRST.write(frst);
	    lastschriftRCUR.write(rcur);
	  }

	  public void add(Zahler zahler) throws SEPAException
	  {
	    if (zahler.getMandatsequence().equals(MandatSequence.FRST))
	    {
	      lastschriftFRST.add(zahler);
	    }
	    else if (zahler.getMandatsequence().equals(MandatSequence.RCUR))
	    {
	      lastschriftRCUR.add(zahler);
	    }
	    else
	      throw new SEPAException("Ung�ltige Sequenz");
	  }

	  public Basislastschrift getLastschriftFRST()
	  {
	    return lastschriftFRST;
	  }

	  public Basislastschrift getLastschriftRCUR()
	  {
	    return lastschriftRCUR;
	  }

	  public ArrayList<Zahler> getZahler()
	  {
	    ArrayList<Zahler> ret = new ArrayList<>();
	    for (Zahler z : lastschriftFRST.getZahler())
	    {
	      ret.add(z);
	    }
	    for (Zahler z : lastschriftRCUR.getZahler())
	    {
	      ret.add(z);
	    }
	    return ret;
	  }

	}
