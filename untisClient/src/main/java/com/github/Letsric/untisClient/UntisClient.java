package com.github.Letsric.untisClient;

import com.bastiaanjansen.otp.HMACAlgorithm;
import com.bastiaanjansen.otp.TOTPGenerator;
import com.github.Letsric.untisClient.ApiTypes.*;
import com.github.Letsric.untisClient.parsedTypes.Class;
import com.github.Letsric.untisClient.parsedTypes.*;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class UntisClient {

    private final String user;
    private final String key;
    private final String school;
    private final String url;

    private final HttpClient client;
    private final Gson parser;

    public UntisClient(String user, String key, String school, String url) {
        this.user = user;
        this.key = key;
        this.school = school;
        this.url = url;

        this.client = HttpClient.newHttpClient();
        this.parser = new Gson();
    }

    public Timetable getTimeTable(String startDateTime, String endDateTime) throws IOException, InterruptedException, UntisElementNotFoundException {

        URI schoolApiUrl = URI.create(String.format(
                "https://%s/WebUntis/jsonrpc_intern.do?school=%s",
                url,
                school.replace(" ", "%20")
        ));

        String payload = String.format("""
                {
                    "id": "GadgetBridge",
                    "jsonrpc": "2.0",
                    "method": "getUserData2017",
                    "params": [
                        {
                            "auth": {
                                "clientTime": %s,
                                "otp": %s,
                                "user": "%s"
                            }
                        }
                    ]
                }
                """,
                getClientTime(),
                getTOTPtoken(key),
                user
        );

        HttpRequest request = HttpRequest.newBuilder(schoolApiUrl)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        untis_Data userData = parser.fromJson(response.body(), untis_Data.class);

        payload = String.format("""
                                {
                                    "id": "GadgetBridge",
                                    "jsonrpc": "2.0",
                                    "method": "getTimetable2017",
                                    "params": [
                                        {
                                            "endDate": "%s",
                                            "id": %s,
                                            "masterDataTimestamp": 0,
                                            "startDate": "%s",
                                            "timetableTimestamp": 0,
                                            "timetableTimestamps": [],
                                            "type": "%s",
                                            "auth": {
                                                "clientTime": %s,
                                                "otp": %s,
                                                "user": "%s"
                                            }
                                        }
                                    ]
                                }
                        """,
                endDateTime, // End time
                userData.result.userData.elemId, // got in step before
                startDateTime, // Start time
                userData.result.userData.elemType, // got in step before
                getClientTime(), // Auth
                getTOTPtoken(key),
                user); // Auth

        request = HttpRequest.newBuilder(schoolApiUrl)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        response = client.send(request, HttpResponse.BodyHandlers.ofString());

        untis_Data data = parser.fromJson(response.body(), untis_Data.class);

        Timetable timetable = new Timetable();

        for (untis_Period untis_period : data.result.timetable.periods) {
            Period period = new Period();

            period.startDateTime = untis_period.startDateTime;
            period.endDateTime = untis_period.endDateTime;

            for (untis_Element untis_element : untis_period.elements) {
                switch (untis_element.type) {
                    case "CLASS":
                        Class class_ = new Class();
                        AtomicReference<untis_Class> untis_class = new AtomicReference<>();
                        Arrays.stream(data.result.masterData.klassen)
                                .filter(x -> x.id == untis_element.id)
                                .findFirst()
                                .ifPresent(untis_class::set);
                        if (untis_class.get() == null) {
                            throw new UntisElementNotFoundException("Class ID " + untis_element.id + " not found in masterData");
                        }
                        class_.name = untis_class.get().name;
                        class_.longName = untis_class.get().longName;
                        period.classes.add(class_);
                        break;
                    case "TEACHER":
                        Teacher teacher = new Teacher();
                        AtomicReference<untis_Teacher> untis_teacher = new AtomicReference<>();
                        Arrays.stream(data.result.masterData.teachers)
                                .filter(x -> x.id == untis_element.id)
                                .findFirst()
                                .ifPresent(untis_teacher::set);
                        if (untis_teacher.get() == null) {
                            throw new UntisElementNotFoundException("Teacher ID " + untis_element.id + " not found in masterData");
                        }
                        teacher.name = untis_teacher.get().name;
                        teacher.firstName = untis_teacher.get().firstName;
                        teacher.lastName = untis_teacher.get().lastName;
                        period.teachers.add(teacher);
                        break;
                    case "SUBJECT":
                        Subject subject = new Subject();
                        AtomicReference<untis_Subject> untis_subject = new AtomicReference<>();
                        Arrays.stream(data.result.masterData.subjects)
                                .filter(x -> x.id == untis_element.id)
                                .findFirst()
                                .ifPresent(untis_subject::set);
                        if (untis_subject.get() == null) {
                            throw new UntisElementNotFoundException("Subject ID " + untis_element.id + " not found in masterData");
                        }
                        subject.name = untis_subject.get().name;
                        subject.longName = untis_subject.get().longName;
                        period.subjects.add(subject);
                        break;
                    case "ROOM":
                        Room room = new Room();
                        AtomicReference<untis_Room> untis_roon = new AtomicReference<>();
                        Arrays.stream(data.result.masterData.rooms)
                                .filter(x -> x.id == untis_element.id)
                                .findFirst()
                                .ifPresent(untis_roon::set);
                        if (untis_roon.get() == null) {
                            throw new UntisElementNotFoundException("Room ID " + untis_element.id + " not found in masterData");
                        }
                        room.name = untis_roon.get().name;
                        room.longName = untis_roon.get().longName;
                        period.rooms.add(room);
                        break;
                }
            }
            timetable.periods.add(period);

        }

        return timetable;
    }

    private static String getClientTime() {
        return String.valueOf(System.currentTimeMillis());
    }

    private static String getTOTPtoken(String key) {
        byte[] secret = key.getBytes();

        TOTPGenerator totp = new TOTPGenerator.Builder(secret)
                .withHOTPGenerator(builder -> {
                    builder.withPasswordLength(6);
                    builder.withAlgorithm(HMACAlgorithm.SHA1);
                })
                .withPeriod(Duration.ofSeconds(30))
                .build();

        return totp.now();
    }
}
