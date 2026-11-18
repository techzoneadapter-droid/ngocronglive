using System;
using System.IO;
using System.Numerics;
public static class ExactStatsClientTest
{
    public static void Main(string[] args)
    {
        byte[] bytes=File.ReadAllBytes(args[0]);
        var state=ExactStatsClient.Decode(bytes);
        var actor=state.Actors[-1007230];
        if(state.Sequence!=123 || state.Map!=84 || actor.Hp!=BigInteger.Pow(10,100) || actor.HpMax!=2*actor.Hp)
            throw new Exception("Java/C# mismatch");
        for(int length=0;length<bytes.Length;length++)
        {
            var broken=new byte[length];Array.Copy(bytes,broken,length);
            try {ExactStatsClient.Decode(broken);throw new Exception("Truncated packet accepted");}
            catch(IOException) {}
        }
        Console.WriteLine("PASS Java -> C# exact stats: signed actor ID, 10^100 values, all truncated packets refused");
    }
}
