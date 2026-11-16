package com.konohahelpdesk.dto.company;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** One weekday's opening window, as the company form sends and receives it. */
public class BusinessHoursDto {

    @NotNull
    private DayOfWeek dayOfWeek;

    /** "09:00" — HH:mm, seconds neither sent nor expected. */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime openTime = LocalTime.of(9, 0);

    @JsonFormat(pattern = "HH:mm")
    private LocalTime closeTime = LocalTime.of(18, 0);

    private boolean closed;

    public BusinessHoursDto() {}

    public BusinessHoursDto(DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean closed) {
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.closed = closed;
    }

    /** Mon–Fri 09:00–18:00, weekend closed — what a new company starts with. */
    public static java.util.List<BusinessHoursDto> defaults() {
        java.util.List<BusinessHoursDto> out = new java.util.ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            boolean weekend = d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
            out.add(new BusinessHoursDto(d, LocalTime.of(9, 0), LocalTime.of(18, 0), weekend));
        }
        return out;
    }

    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
}
