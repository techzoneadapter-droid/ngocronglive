using System;
using System.Text;
using UnityEngine;

public static class NroServerList
{
    [Serializable] private class Entry { public string nameServer; public string address; public int port; public int language; }
    [Serializable] private class Wrapper { public Entry[] items; }
    public static string Normalize(string value)
    {
        if (string.IsNullOrEmpty(value)) throw new ArgumentException("Empty server list");
        if (!value.TrimStart().StartsWith("[")) return value;
        var wrapper = JsonUtility.FromJson<Wrapper>("{\"items\":" + value + "}");
        if (wrapper == null || wrapper.items == null || wrapper.items.Length == 0) throw new ArgumentException("Invalid server list");
        var result = new StringBuilder();
        foreach (var entry in wrapper.items) {
            if (entry == null || string.IsNullOrEmpty(entry.nameServer) || string.IsNullOrEmpty(entry.address)
                || entry.nameServer.IndexOfAny(new[] { ':', ',' }) >= 0 || entry.address.IndexOfAny(new[] { ':', ',' }) >= 0
                || entry.port < 1 || entry.port > 32767 || entry.language < 0 || entry.language > 2)
                throw new ArgumentException("Unsupported server entry");
            result.Append(entry.nameServer).Append(':').Append(entry.address).Append(':').Append(entry.port).Append(':').Append(entry.language).Append(',');
        }
        return result.Append("0,0").ToString();
    }
}
