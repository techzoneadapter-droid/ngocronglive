using Mono.Cecil;
using Mono.Cecil.Cil;

if (args.Length != 2) throw new ArgumentException("Usage: ClientPatcher input.dll output.dll");
using var assembly = AssemblyDefinition.ReadAssembly(args[0]);
var types = assembly.MainModule.Types;
var screen = types.Single(t => t.Name == "ServerListScreen");
var load = screen.Methods.Single(m => m.Name == "loadIP" && m.Parameters.Count == 0);
var parse = screen.Methods.Single(m => m.Name == "getServerList" && m.Parameters.Count == 1);
var connect = types.Single(t => t.Name == "SplashScr").Methods.Single(m => m.Name == "loadIP" && m.Parameters.Count == 0);
load.Body = new MethodBody(load);
var il = load.Body.GetILProcessor();
il.Emit(OpCodes.Ldstr, "Ngoc Rong Live:127.0.0.1:14445:0,0,0");
il.Emit(OpCodes.Call, parse);
il.Emit(OpCodes.Call, connect);
il.Emit(OpCodes.Ret);
var update = screen.Methods.Single(m => m.Name == "doUpdateServer" && m.Parameters.Count == 0);
update.Body = new MethodBody(update);
il = update.Body.GetILProcessor();
il.Emit(OpCodes.Call, load);
il.Emit(OpCodes.Ret);
assembly.Write(args[1]);
using var verified = AssemblyDefinition.ReadAssembly(args[1]);
var result = verified.MainModule.Types.Single(t => t.Name == "ServerListScreen")
    .Methods.Single(m => m.Name == "loadIP" && m.Parameters.Count == 0);
if (result.Body.Instructions.Count != 4 || result.Body.Instructions[0].Operand as string != "Ngoc Rong Live:127.0.0.1:14445:0,0,0")
    throw new InvalidDataException("Client endpoint patch verification failed.");
Console.WriteLine("Patched and verified local endpoint 127.0.0.1:14445. Player executable is the pinned upstream binary.");
