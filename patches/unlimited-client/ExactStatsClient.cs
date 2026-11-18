using System;
using System.IO;
using System.Collections.Generic;
using System.Numerics;

// Shared pure decoder for PC/Android/WebGL. UI and negotiated transport hook are separate.
public static class ExactStatsClient
{
    public sealed class Actor
    {
        public long Id;
        public BigInteger Hp, HpMax, Ki, KiMax, Attack, Defense;
    }
    public sealed class Snapshot
    {
        public long Sequence;
        public int Map, Zone;
        public readonly Dictionary<long, Actor> Actors = new Dictionary<long, Actor>();
    }
    public static Snapshot Decode(byte[] bytes)
    {
        using (var stream = new MemoryStream(bytes, false))
        {
            if (Int(stream) != 0x4e455831 || Byte(stream) != 1) throw new IOException("Unknown exact stats format");
            var result = new Snapshot { Sequence = Long(stream), Map = Int(stream), Zone = Int(stream) };
            int count = UShort(stream);
            if (result.Sequence < 0 || result.Map < 0 || result.Zone < 0 || count > 128) throw new IOException("Invalid exact snapshot");
            for (int index = 0; index < count; index++)
            {
                var actor = new Actor { Id = Long(stream), Hp = Number(stream), HpMax = Number(stream),
                    Ki = Number(stream), KiMax = Number(stream), Attack = Number(stream), Defense = Number(stream) };
                if (actor.Hp > actor.HpMax || actor.Ki > actor.KiMax || result.Actors.ContainsKey(actor.Id))
                    throw new IOException("Invalid exact actor");
                result.Actors.Add(actor.Id, actor);
            }
            if (stream.Position != stream.Length) throw new IOException("Trailing exact stats data");
            return result;
        }
    }
    private static int Byte(Stream stream)
    {
        int value = stream.ReadByte();
        if (value < 0) throw new EndOfStreamException();
        return value;
    }
    private static int UShort(Stream stream) { return (Byte(stream) << 8) | Byte(stream); }
    private static int Int(Stream stream)
    {
        return unchecked((Byte(stream) << 24) | (Byte(stream) << 16) | (Byte(stream) << 8) | Byte(stream));
    }
    private static long Long(Stream stream)
    {
        return unchecked(((long)Int(stream) << 32) | (uint)Int(stream));
    }
    private static BigInteger Number(Stream stream)
    {
        int length = UShort(stream);
        if (length < 1 || length > 4096 || length > stream.Length - stream.Position) throw new IOException("Invalid decimal length");
        BigInteger value = BigInteger.Zero;
        for (int index = 0; index < length; index++)
        {
            int digit = Byte(stream);
            if (digit < '0' || digit > '9' || (index == 0 && length > 1 && digit == '0'))
                throw new IOException("Invalid decimal digit");
            value = value * 10 + digit - '0';
        }
        return value;
    }
}
