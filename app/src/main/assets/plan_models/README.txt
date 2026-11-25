Put your own 3D models here, one per plan:
  plan_01.glb  plan_02.glb  ...  plan_17.glb
If a file exists, the app shows that model for the plan (on Home and in the plan screen).
If not, the built-in 3D scene is used.

Supported:  binary glTF (.glb), meshes, node transforms, base colour, emissive colour, alpha (BLEND).
Not supported: textures, skins/rigging, glTF animations, Draco or meshopt compression
               (export with "no compression"), more than 65000 vertices in one mesh.
Tip: Blender > File > Export > glTF 2.0 > format "glTB Binary (.glb)", Compression off, Apply Modifiers on.
The model is centred and scaled to fit automatically; it spins slowly and reacts to the AI state.
