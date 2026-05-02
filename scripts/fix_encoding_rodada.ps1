$f = "C:\Dev\ManagerFoot\app\src\main\java\br\com\managerfoot\presentation\ui\screens\RodadaScreen.kt"
$txt = [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8)

# Mapa de substituicoes: sequencias corrompidas -> caractere correto
$replacements = @{
    'SÃ©rie'  = 'Série'
    'Sé©rie'  = 'Série'
    'â€"'     = '—'
    'Ã©'      = 'é'
    'Ã£'      = 'ã'
    'Ã§'      = 'ç'
    'Ã­'      = 'í'
    'Ãº'      = 'ú'
    'Ã—'      = '×'
    'â"€'     = '─'
    'Ã³'      = 'ó'
    'Ã¡'      = 'á'
    'Ã '      = 'à'
    'Ãµ'      = 'õ'
    'Ã®'      = 'î'
    'Ã¢'      = 'â'
    'Ãª'      = 'ê'
    'Ã´'      = 'ô'
    'Ã¼'      = 'ü'
}

foreach ($key in $replacements.Keys) {
    $txt = $txt.Replace($key, $replacements[$key])
}

[System.IO.File]::WriteAllText($f, $txt, (New-Object System.Text.UTF8Encoding $false))
Write-Host "Encoding fixed. Lines: $($txt.Split("`n").Count)"
