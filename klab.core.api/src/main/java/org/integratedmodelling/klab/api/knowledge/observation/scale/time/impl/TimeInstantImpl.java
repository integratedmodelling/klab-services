//package org.integratedmodelling.klab.api.knowledge.observation.scale.time.impl;
//
//import java.time.Instant;
//import java.time.LocalDate;
//import java.time.LocalTime;
//import java.time.ZoneId;
//import java.time.ZonedDateTime;
//import java.time.temporal.ChronoUnit;
//import java.util.Objects;
//
//import org.integratedmodelling.klab.api.exceptions.KValidationException;
//import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution;
//import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution.Type;
//import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
//
//public class TimeInstantImpl implements TimeInstant {
//
//	private static final long serialVersionUID = -2663390456592040237L;
//
//    ZonedDateTime time;
//	static ZoneId utc = ZoneId.of("UTC");
//
//    public static TimeInstant create(int year, int month, int day) {
//        return new TimeInstantImpl(year, month, day);
//    }
//
//    public static TimeInstant create(int year) {
//        return new TimeInstantImpl(year);
//    }
//
//    public static TimeInstant create(ZonedDateTime time) {
//        return new TimeInstantImpl(time);
//    }
//
//    public static TimeInstant create(long milliseconds) {
//        return new TimeInstantImpl(milliseconds);
//    }
//
//    public static TimeInstant create() {
//        return new TimeInstantImpl();
//    }
//
//    public TimeInstantImpl(int year) {
//        time = ZonedDateTime.of(LocalDate.of( year, 1, 1), LocalTime.of(0, 0), utc);
//    }
//
//    public TimeInstantImpl(int year, int month, int day) {
//        time = ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(0, 0), utc);
//    }
//
//    public TimeInstantImpl(long milliseconds) {
//        time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(milliseconds), utc);
//    }
//
//    public TimeInstantImpl(ZonedDateTime time) {
//        this.time = time;
//    }
//
//    public TimeInstantImpl() {
//        this.time = ZonedDateTime.now(utc);
//    }
//
//    @Override
//    public int compareTo(TimeInstant arg0) {
//        return time.compareTo(((TimeInstantImpl) arg0).time);
//    }
//
//    @Override
//    public long getMilliseconds() {
//        return time.toInstant().toEpochMilli();
//    }
//
//    public ZonedDateTime asDate() {
//        return time;
//    }
//
//    public String describe(Resolution resolution) {
//        if (resolution.getType() == Type.YEAR) {
//            return "" + getYear();
//        } else if (resolution.getType() == Type.MONTH) {
//            return getMonth() + "/" + getYear();
//        } else if (resolution.getType() == Type.DAY) {
//            return getDay() + "/" + getMonth() + "/" + getYear();
//        }
//        return toString();
//    }
//
//    public String toString() {
//        return DateTimeFormat.shortDateTime().print(time);
//    }
//
//    @Override
//    public boolean isAfter(TimeInstant t) {
//        return this.time.isAfter(((TimeInstantImpl) t).time);
//    }
//
//    @Override
//    public int getDayOfYear() {
//        return this.time.getDayOfYear() - 1;
//    }
//
//    @Override
//    public boolean isBefore(TimeInstant t) {
//        return this.time.isBefore(((TimeInstantImpl) t).time);
//    }
//
//    @Override
//    public String getSpecification() {
//        // TODO Auto-generated method stub
//        return "todo";
//    }
//
//    @Override
//    public int getYear() {
//        return this.time.getYear();
//    }
//
//    @Override
//    public TimeInstant plus(int periods, Resolution resolution) {
//        switch(resolution.getType()) {
//        case CENTURY:
//            return new TimeInstantImpl(time.plusYears((int) (resolution.getMultiplier() * 100 * periods)));
//        case DAY:
//            return new TimeInstantImpl(time.plusDays((int) (resolution.getMultiplier() * periods)));
//        case DECADE:
//            return new TimeInstantImpl(time.plusYears((int) (resolution.getMultiplier() * 10 * periods)));
//        case HOUR:
//            return new TimeInstantImpl(time.plusHours((int) resolution.getMultiplier() * periods));
//        case MILLENNIUM:
//            return new TimeInstantImpl(time.plusYears((int) (resolution.getMultiplier() * 1000 * periods)));
//        case MILLISECOND:
//            return new TimeInstantImpl(getMilliseconds() + ((int) resolution.getMultiplier() * periods));
//        case MINUTE:
//            return new TimeInstantImpl(time.plusMinutes((int) resolution.getMultiplier() * periods));
//        case MONTH:
//            return new TimeInstantImpl(time.plusMonths((int) resolution.getMultiplier() * periods));
//        case SECOND:
//            return new TimeInstantImpl(time.plusSeconds((int) resolution.getMultiplier() * periods));
//        case WEEK:
//            return new TimeInstantImpl(time.plusWeeks((int) resolution.getMultiplier() * periods));
//        case YEAR:
//            return new TimeInstantImpl(time.plusYears((int) resolution.getMultiplier() * periods));
//        }
//        throw new KValidationException("wrong resolution passed to ITimeInstant::plus");
//    }
//
//    @Override
//    public TimeInstant minus(int periods, Resolution resolution) {
//        switch(resolution.getType()) {
//        case CENTURY:
//            return new TimeInstantImpl(time.minusYears((int) (resolution.getMultiplier() * 100 * periods)));
//        case DAY:
//            return new TimeInstantImpl(time.minusDays((int) (resolution.getMultiplier() * periods)));
//        case DECADE:
//            return new TimeInstantImpl(time.minusYears((int) (resolution.getMultiplier() * 10 * periods)));
//        case HOUR:
//            return new TimeInstantImpl(time.minusHours((int) resolution.getMultiplier() * periods));
//        case MILLENNIUM:
//            return new TimeInstantImpl(time.minusYears((int) (resolution.getMultiplier() * 1000 * periods)));
//        case MILLISECOND:
//            return new TimeInstantImpl(getMilliseconds() - ((int) resolution.getMultiplier() * periods));
//        case MINUTE:
//            return new TimeInstantImpl(time.minusMinutes((int) resolution.getMultiplier() * periods));
//        case MONTH:
//            return new TimeInstantImpl(time.minusMonths((int) resolution.getMultiplier() * periods));
//        case SECOND:
//            return new TimeInstantImpl(time.minusSeconds((int) resolution.getMultiplier() * periods));
//        case WEEK:
//            return new TimeInstantImpl(time.minusWeeks((int) resolution.getMultiplier() * periods));
//        case YEAR:
//            return new TimeInstantImpl(time.minusYears((int) resolution.getMultiplier() * periods));
//        }
//        throw new KValidationException("wrong resolution passed to ITimeInstant::plus");
//    }
//
//    @Override
//    public long getPeriods(TimeInstant other, Resolution resolution) {
//
//        ZonedDateTime start = this.time;
//        ZonedDateTime end = ((TimeInstantImpl) other).time;
//
//        if (start.isAfter(end)) {
//            start = end;
//            end = this.time;
//        }
//
//        switch(resolution.getType()) {
//        case CENTURY:
//            return ChronoUnit.YEARS.between(start, end) / (int) (100 * resolution.getMultiplier());
//        case DAY:
//            return ChronoUnit.DAYS.between(start, end) / (int) resolution.getMultiplier();
//        case DECADE:
//            return ChronoUnit.YEARS.between(start, end) / (int) (10 * resolution.getMultiplier());
//        case HOUR:
//            return ChronoUnit.HOURS.between(start, end) / (int) resolution.getMultiplier();
//        case MILLENNIUM:
//            return ChronoUnit.YEARS.between(start, end) / (int) (1000 * resolution.getMultiplier());
//        case MILLISECOND:
//            return end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli() / (int) resolution.getMultiplier();
//        case MINUTE:
//            return ChronoUnit.MINUTES.between(start, end) / (int) resolution.getMultiplier();
//        case MONTH:
//            return ChronoUnit.MONTHS.between(start, end) / (int) resolution.getMultiplier();
//        case SECOND:
//            return ChronoUnit.SECONDS.between(start, end) / (int) resolution.getMultiplier();
//        case WEEK:
//            return ChronoUnit.WEEKS.between(start, end) / (int) resolution.getMultiplier();
//        case YEAR:
//            return ChronoUnit.YEARS.between(start, end) / (int) resolution.getMultiplier();
//        }
//
//        throw new KValidationException("wrong resolution passed to ITimeInstant::getPeriods");
//    }
//
//    @Override
//    public boolean isAlignedWith(Resolution res) {
//
//        switch(res.getType()) {
//        case CENTURY:
//            return time.getYear() % 100 == 0 && time.getSecondOfDay() == 0 && time.getDayOfYear() == 1;
//        case DAY:
//            return time.getMinuteOfDay() == 0;
//        case DECADE:
//            return time.getYear() % 10 == 0 && time.getSecondOfDay() == 0 && time.getDayOfYear() == 1;
//        case HOUR:
//            return time.getMinuteOfHour() == 0;
//        case MILLENNIUM:
//            return time.getYear() % 1000 == 0 && time.getSecondOfDay() == 0 && time.getDayOfYear() == 1;
//        case MILLISECOND:
//            return true;
//        case MINUTE:
//            return time.getSecondOfMinute() == 0;
//        case MONTH:
//            return time.getDayOfMonth() == 1 && time.getSecondOfDay() == 0;
//        case SECOND:
//            return time.getMillis() % 1000 == 0;
//        case WEEK:
//            return time.getDayOfWeek() == 1 && time.getSecondOfDay() == 0;
//        case YEAR:
//            return time.getSecondOfDay() == 0 && time.getDayOfYear() == 1;
//        }
//
//        return false;
//    }
//
//    @Override
//    public int getDay() {
//        return time.getDayOfMonth();
//    }
//
//    @Override
//    public int getMonth() {
//        return time.getMonthOfYear();
//    }
//
//    @Override
//    public int getHour() {
//        return time.getHourOfDay();
//    }
//
//    @Override
//    public int getMinute() {
//        return time.getMinuteOfHour();
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(time);
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        if (this == obj)
//            return true;
//        if (obj == null)
//            return false;
//        if (getClass() != obj.getClass())
//            return false;
//        TimeInstant other = (TimeInstant) obj;
//        return Objects.equals(time, other.time);
//    }
//
//	@Override
//	public TimeInstant beginOf(Type temporalAggregation) {
//        switch(temporalAggregation) {
//        case CENTURY:
//            return new TimeInstantImpl(
//            		time.getYear() - getYear() % 100,
//                    1,
//                    1,
//                    0,
//                    0,
//                    0,
//                    0);
//        case DAY:
//            return new TimeInstant(new DateTime(
//            		time.getYear(),
//                    time.getMonthOfYear(),
//                    time.getDayOfMonth(),
//                    0,
//                    0,
//                    0,
//                    0,
//                    time.getZone()));
//        case DECADE:
//            return new TimeInstant(new DateTime(
//            		time.getYear() - getYear() % 10,
//                    1,
//                    1,
//                    0,
//                    0,
//                    0,
//                    0,
//                    time.getZone()));
//        case HOUR:
//            return new TimeInstant(new DateTime(
//            		time.getYear(),
//                    time.getMonthOfYear(),
//                    time.getDayOfMonth(),
//                    time.getHourOfDay(),
//                    0,
//                    0,
//                    0,
//                    time.getZone()));
//        case MILLENNIUM:
//            return new TimeInstant(new DateTime(
//            		time.getYear() - getYear() % 1000,
//                    1,
//                    1,
//                    0,
//                    0,
//                    0,
//                    0,
//                    time.getZone()));
//        case MILLISECOND:
//            return new TimeInstant(new DateTime(
//            		time.getYear(),
//                    time.getMonthOfYear(),
//                    time.getDayOfMonth(),
//                    time.getHourOfDay(),
//                    time.getMinuteOfHour(),
//                    time.getSecondOfMinute(),
//                    0,
//                    time.getZone()));
//        case MINUTE:
//            return new TimeInstant(new DateTime(
//            		time.getYear(),
//                    time.getMonthOfYear(),
//                    time.getDayOfMonth(),
//                    time.getHourOfDay(),
//                    0,
//                    0,
//                    0,
//                    time.getZone()));
//        case MONTH:
//            return new TimeInstant(new DateTime(
//            		time.getYear(),
//                    time.getMonthOfYear(),
//                    1,
//                    0,
//                    0,
//                    0,
//                    0,
//                    time.getZone()));
//        case SECOND:
//            return new TimeInstant(new DateTime(
//            		time.getYear(),
//                    time.getMonthOfYear(),
//                    time.getDayOfMonth(),
//                    time.getHourOfDay(),
//                    time.getMinuteOfHour(),
//                    time.getSecondOfMinute(),
//                    0,
//                    time.getZone()));
//        case WEEK:
//        	DateTime monday = time.withDayOfWeek(DateTimeConstants.MONDAY);
//            return new TimeInstant(new DateTime(
//            		monday.getYear(),
//                    monday.getMonthOfYear(),
//                    monday.getDayOfMonth(),
//                    0,
//                    0,
//                    0,
//                    0,
//                    time.getZone()));
//        case YEAR:
//            return new TimeInstant(new DateTime(
//            		time.getYear(),
//                    1,
//                    1,
//                    0,
//                    0,
//                    0,
//                    0,
//                    time.getZone()));
//        }
//
//        throw new KlabInternalErrorException("cannot adjust time to " + temporalAggregation);
//	}
//
//	@Override
//	public TimeInstant endOf(Type temporalAggregation) {
//		return beginOf(temporalAggregation).plus(1, Time.resolution(1, temporalAggregation));
//	}
//
//	@Override
//	public String toRFC3339String() {
//		return ISODateTimeFormat.dateTime().print(getMilliseconds());
//	}
//
//
//}
