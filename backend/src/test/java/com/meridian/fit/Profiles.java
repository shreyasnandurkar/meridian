package com.meridian.fit;

import com.meridian.fit.FitScorer.FitMember;
import com.meridian.fit.GroupFitCalculator.GroupMember;
import com.meridian.profile.ProfileModels.Budget;
import com.meridian.profile.ProfileModels.TravelProfile;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small builder for test profiles. */
public final class Profiles {

    private Map<String, Double> interests = Map.of();
    private String pace = "balanced";
    private Budget budget = new Budget("mid", "mid", "mid");
    private int crowd = 50;
    private int gems = 50;
    private List<String> dietary = List.of();
    private List<String> access = List.of();
    private List<String> must = List.of();
    private List<String> never = List.of();

    public static Profiles profile() {
        return new Profiles();
    }

    public Profiles interests(Object... pairs) {
        java.util.Map<String, Double> m = new java.util.TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        this.interests = m;
        return this;
    }

    public Profiles pace(String pace) {
        this.pace = pace;
        return this;
    }

    public Profiles budget(String stay, String food, String activities) {
        this.budget = new Budget(stay, food, activities);
        return this;
    }

    public Profiles crowd(int crowd) {
        this.crowd = crowd;
        return this;
    }

    public Profiles gems(int gems) {
        this.gems = gems;
        return this;
    }

    public Profiles dietary(String... d) {
        this.dietary = List.of(d);
        return this;
    }

    public Profiles access(String... a) {
        this.access = List.of(a);
        return this;
    }

    public Profiles mustHaves(String... m) {
        this.must = List.of(m);
        return this;
    }

    public Profiles dealbreakers(String... d) {
        this.never = List.of(d);
        return this;
    }

    public TravelProfile build() {
        return new TravelProfile(interests, pace, budget, crowd, gems, dietary, access, must, never, null);
    }

    public FitMember member(String name) {
        return new FitMember(UUID.nameUUIDFromBytes(name.getBytes()), name, build(), List.of());
    }

    public GroupMember group(String name) {
        return new GroupMember(member(name), null, null, false);
    }

    public static GroupMember withBudget(GroupMember m, int min, int max, boolean visible) {
        return new GroupMember(m.fit(), BigDecimal.valueOf(min), BigDecimal.valueOf(max), visible);
    }

    public static GroupMember noProfile(String name) {
        return new GroupMember(new FitMember(UUID.nameUUIDFromBytes(name.getBytes()), name, null, List.of()), null,
                null, false);
    }
}
