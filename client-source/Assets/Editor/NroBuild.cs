using System;
using System.Linq;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEditor.Rendering;
using UnityEngine;

public static class NroBuild
{
    public static void Windows()
    {
        const string scene = "Assets/Scenes/SampleScene.unity";
        if (!System.IO.File.Exists(scene)) throw new Exception("Entry scene missing");
        // Runtime material creation must retain its fallback shader in the player.
        var shader = Shader.Find("Sprites/Default");
        if (shader == null) throw new Exception("Fallback shader missing");
        if (!AssetDatabase.IsValidFolder("Assets/Resources")) AssetDatabase.CreateFolder("Assets", "Resources");
        const string material = "Assets/Resources/NroFallback.mat";
        if (AssetDatabase.LoadAssetAtPath<Material>(material) == null) AssetDatabase.CreateAsset(new Material(shader), material);
        AssetDatabase.SaveAssets();
        PlayerSettings.companyName = "NgocRongLive";
        PlayerSettings.productName = "NgocRongLive Source Preview";
        PlayerSettings.defaultScreenWidth = 1024;
        PlayerSettings.defaultScreenHeight = 600;
        PlayerSettings.runInBackground = true;
        PlayerSettings.SetScriptingBackend(BuildTargetGroup.Standalone, ScriptingImplementation.Mono2x);
        var output = Environment.GetEnvironmentVariable("NRO_CLIENT_OUTPUT");
        if (string.IsNullOrEmpty(output)) throw new Exception("NRO_CLIENT_OUTPUT is required");
        var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions {
            scenes = new[] { scene }, locationPathName = output,
            target = BuildTarget.StandaloneWindows64, options = BuildOptions.Development
        });
        if (report.summary.result != BuildResult.Succeeded) throw new Exception("Client build failed: " + report.summary.result);
    }
}
