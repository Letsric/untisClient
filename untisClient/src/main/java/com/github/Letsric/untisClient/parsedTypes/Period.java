package com.github.Letsric.untisClient.parsedTypes;

import java.util.ArrayList;

public class Period {

    public String startDateTime;
    public String endDateTime;
    public ArrayList<Class> classes = new ArrayList<>();
    public ArrayList<Subject> subjects = new ArrayList<>();
    public ArrayList<Room> rooms = new ArrayList<>();
    public ArrayList<Teacher> teachers = new ArrayList<>();

}
