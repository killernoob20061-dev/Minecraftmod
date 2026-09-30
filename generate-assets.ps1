# Reproducible placeholder art. Run from any directory with PowerShell on Windows.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assetRoot = Join-Path $PSScriptRoot '../src/main/resources/assets/worldeater'
$textureDir = Join-Path $assetRoot 'textures/item'
$modelDir = Join-Path $assetRoot 'models/item'
New-Item -ItemType Directory -Force $textureDir, $modelDir | Out-Null
$bitmap = [System.Drawing.Bitmap]::new(16, 16)
try {
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $dx = $x - 7.5
            $dy = $y - 7.5
            $distance = [Math]::Sqrt($dx * $dx + $dy * $dy)
            $color = [System.Drawing.Color]::Transparent
            if ($distance -le 5.8) {
                $color = [System.Drawing.Color]::FromArgb(255, 17, 12, 29)
            }
            if ($distance -gt 4.3 -and $distance -le 6.3) {
                $color = [System.Drawing.Color]::FromArgb(255, 135, 76, 214)
            }
            if ([Math]::Abs($dy + 0.35 * $dx) -lt 1.2 -and [Math]::Abs($dx) -le 7) {
                $color = [System.Drawing.Color]::FromArgb(255, 207, 165, 255)
            }
            if ($distance -le 3.4) {
                $color = [System.Drawing.Color]::FromArgb(255, 8, 6, 14)
            }
            $bitmap.SetPixel($x, $y, $color)
        }
    }
    $bitmap.Save((Join-Path $textureDir 'test_item.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $bitmap.Dispose()
}
@'
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "worldeater:item/test_item"
  }
}
'@ | Set-Content -Encoding utf8 (Join-Path $modelDir 'test_item.json')
Write-Output 'Generated test_item.png (16x16) and models/item/test_item.json.'

$cannon = [System.Drawing.Bitmap]::new(16, 16)
try {
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $color = [System.Drawing.Color]::Transparent
            if ($x -ge 3 -and $x -le 13 -and $y -ge 4 -and $y -le 9) {
                $color = [System.Drawing.Color]::FromArgb(255, 37, 29, 55)
                if ($y -eq 4 -or $y -eq 9) { $color = [System.Drawing.Color]::FromArgb(255, 108, 66, 162) }
            }
            if ($x -ge 4 -and $x -le 6 -and $y -ge 9 -and $y -le 13) {
                $color = [System.Drawing.Color]::FromArgb(255, 57, 45, 76)
            }
            if ($x -ge 12 -and $x -le 14 -and $y -ge 5 -and $y -le 8) {
                $color = [System.Drawing.Color]::FromArgb(255, 190, 135, 250)
            }
            $cannon.SetPixel($x, $y, $color)
        }
    }
    $cannon.Save((Join-Path $textureDir 'singularity_cannon.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $cannon.Dispose() }
@'
{
  "parent": "minecraft:item/handheld",
  "textures": { "layer0": "worldeater:item/singularity_cannon" }
}
'@ | Set-Content -Encoding utf8 (Join-Path $modelDir 'singularity_cannon.json')
$entityDir = Join-Path $assetRoot 'textures/entity'
New-Item -ItemType Directory -Force $entityDir | Out-Null
$white = [System.Drawing.Bitmap]::new(16, 16)
try {
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $white.SetPixel($x, $y, [System.Drawing.Color]::White) } }
    $white.Save((Join-Path $entityDir 'singularity.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $white.Dispose() }
Write-Output 'Generated singularity_cannon item texture/model and tintable singularity entity texture (16x16).'

$deathStar = [System.Drawing.Bitmap]::new(16, 16)
try {
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $dx = $x - 7.5; $dy = $y - 7.5
        $color = [System.Drawing.Color]::Transparent
        if ($dx * $dx + $dy * $dy -le 48) {
            $shade = [int](165 - 4 * $dx - 3 * $dy)
            if ($y % 3 -eq 0 -and $x % 4 -ne 0) { $shade -= 22 }
            if ($y -eq 8) { $shade = 52 }
            $color = [System.Drawing.Color]::FromArgb(255, $shade, $shade, $shade)
            $dish = ($x - 5) * ($x - 5) + ($y - 5) * ($y - 5)
            if ($dish -le 6) { $color = [System.Drawing.Color]::FromArgb(255, 69, 74, 73) }
            if ($dish -le 1) { $color = [System.Drawing.Color]::FromArgb(255, 128, 192, 104) }
        }
        $deathStar.SetPixel($x, $y, $color)
    } }
    $deathStar.Save((Join-Path $textureDir 'death_star.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $deathStar.Dispose() }
@'
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "worldeater:item/death_star" }
}
'@ | Set-Content -Encoding utf8 (Join-Path $modelDir 'death_star.json')
Write-Output 'Generated death_star item texture/model (16x16).'

$trigger = [System.Drawing.Bitmap]::new(16, 16)
try {
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $color = [System.Drawing.Color]::Transparent
        if ($x -ge 4 -and $x -le 11 -and $y -ge 2 -and $y -le 13) {
            $color = [System.Drawing.Color]::FromArgb(255, 42, 47, 48)
            if ($x -eq 4 -or $x -eq 11) { $color = [System.Drawing.Color]::FromArgb(255, 139, 150, 151) }
            if ($x -ge 6 -and $x -le 9 -and $y -ge 4 -and $y -le 7) { $color = [System.Drawing.Color]::FromArgb(255, 104, 242, 56) }
            if ($x -ge 7 -and $x -le 8 -and $y -ge 10 -and $y -le 11) { $color = [System.Drawing.Color]::FromArgb(255, 222, 49, 37) }
        }
        $trigger.SetPixel($x, $y, $color)
    } }
    $trigger.Save((Join-Path $textureDir 'superlaser_trigger.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $trigger.Dispose() }
@'
{
  "parent": "minecraft:item/handheld",
  "textures": { "layer0": "worldeater:item/superlaser_trigger" }
}
'@ | Set-Content -Encoding utf8 (Join-Path $modelDir 'superlaser_trigger.json')
Write-Output 'Generated superlaser trigger texture/model (16x16).'
