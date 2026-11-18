using System;
using System.Collections.Generic;
using System.Numerics;

public sealed class ExactStatsState
{
    private readonly Dictionary<long, ExactStatsClient.Actor> actors = new Dictionary<long, ExactStatsClient.Actor>();
    private long sequence = -1;
    private int map = -1, zone = -1;
    public bool Apply(byte[] packet, int currentMap, int currentZone)
    {
        var snapshot = ExactStatsClient.Decode(packet);
        if (snapshot.Map != currentMap || snapshot.Zone != currentZone) return false;
        if (snapshot.Map != map || snapshot.Zone != zone) { actors.Clear(); sequence = -1; }
        if (snapshot.Sequence <= sequence) return false;
        map = snapshot.Map; zone = snapshot.Zone; sequence = snapshot.Sequence;
        foreach (var actor in snapshot.Actors) actors[actor.Key] = actor.Value;
        return true;
    }
    public ExactStatsClient.Actor Get(long id, int currentMap, int currentZone)
    {
        ExactStatsClient.Actor actor;
        return map == currentMap && zone == currentZone && actors.TryGetValue(id, out actor) ? actor : null;
    }
    public void Clear() { actors.Clear(); map = zone = -1; sequence = -1; }
    public static string Compact(BigInteger value)
    {
        string digits = value.ToString(System.Globalization.CultureInfo.InvariantCulture);
        if (digits.Length <= 9) return value.ToString("N0", System.Globalization.CultureInfo.GetCultureInfo("vi-VN"));
        string[] units = { "", "k", "tr", "tỷ", "nghìn tỷ", "triệu tỷ" };
        int group = (digits.Length - 1) / 3;
        if (group >= units.Length) return digits[0] + "," + digits.Substring(1, 2) + "e" + (digits.Length - 1);
        int leading = digits.Length - group * 3;
        string fraction = digits.Substring(leading, System.Math.Min(2, digits.Length - leading)).TrimEnd('0');
        return digits.Substring(0, leading) + (fraction.Length > 0 ? "," + fraction : "") + " " + units[group];
    }
}
