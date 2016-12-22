package org.bitbucket.ouyi.business;

import liquibase.util.csv.opencsv.CSVParser;
import org.bitbucket.ouyi.db.PersonDAO;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.function.Function;

/**
 * Created by worker on 12/21/16.
 */
public class Transformer {

    public static final String DATE_TIME_PATTERN = "MM-dd-yyyy HH:mm:ss";
    public static final int ID_INDEX = 0;
    public static final int NAME_INDEX = 1;
    public static final int TIME_INDEX = 2;

    // Assumption: time_of_start in default timezone
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN).withZone(TimeZone.getDefault().toZoneId());

    private final CSVParser parser = new CSVParser();

    private String uploadRootDir;

    private PersonDAO personDAO;

    protected Function<String, String[]> parseLine = l -> {
        try {
            return parser.parseLine(l);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    };

    // Assumption: Obs. is the last element
    protected Function<String[], String[]> removeObs = s -> Arrays.copyOfRange(s, 0, s.length - 1);

    protected Function<String[], String[]> nameToLowercase = s -> {
        s[NAME_INDEX] = s[NAME_INDEX].toLowerCase();
        return s;
    };

    protected Function<String[], String[]> timeToUTC = s -> {
        ZonedDateTime utc = ZonedDateTime.parse(s[TIME_INDEX], formatter).withZoneSameInstant(ZoneOffset.UTC);
        s[TIME_INDEX] = utc.format(formatter);
        return s;
    };

    protected Function<String[], Person> toPerson = s -> {
        ZonedDateTime utc = ZonedDateTime.parse(s[TIME_INDEX], formatter).withZoneSameInstant(ZoneOffset.UTC);
        return new Person(Integer.parseInt(s[ID_INDEX]), s[NAME_INDEX], utc);
    };

    public Transformer(String uploadRootDir, PersonDAO personDAO) {
        this.uploadRootDir = uploadRootDir;
        this.personDAO = personDAO;
    }

    public void transform(String filename) throws Exception {
        Iterator<Person> iterator = Files.lines(Paths.get(uploadRootDir, filename))
                .map(parseLine
                        .andThen(removeObs
                                .andThen(nameToLowercase
                                        .andThen(timeToUTC.andThen(toPerson))))).distinct().iterator();
        personDAO.insertAll(iterator);
    }

}