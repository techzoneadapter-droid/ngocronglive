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
        var cache=new ExactStatsState();
        if(cache.Apply(bytes,84,1))throw new Exception("Cross-zone snapshot accepted");
        if(!cache.Apply(bytes,84,0)||cache.Apply(bytes,84,0))throw new Exception("Snapshot ordering failed");
        if(cache.Get(-1007230,84,0)==null||cache.Get(-1007230,84,1)!=null)throw new Exception("Map scoped cache failed");
        if(ExactStatsState.Compact(BigInteger.Pow(10,100))!="1,00e100")throw new Exception("Huge value formatting failed");
        cache.Clear();if(cache.Get(-1007230,84,0)!=null||!cache.Apply(bytes,84,0))throw new Exception("Reconnect cache reset failed");
        for(int length=0;length<bytes.Length;length++)
        {
            var broken=new byte[length];Array.Copy(bytes,broken,length);
            try {ExactStatsClient.Decode(broken);throw new Exception("Truncated packet accepted");}
            catch(IOException) {}
        }
        Console.WriteLine("PASS Java -> C# exact stats: signed actor ID, 10^100 values, all truncated packets refused");
    }
}
