param()
$N = 50000
$MEDIA = 2.7
$MULT = 0.55   # multiplicador de dominancia atual

$cenarios = @(
    [PSCustomObject]@{fC=70;fF=70},
    [PSCustomObject]@{fC=75;fF=65},
    [PSCustomObject]@{fC=80;fF=60},
    [PSCustomObject]@{fC=90;fF=60},
    [PSCustomObject]@{fC=100;fF=60},
    [PSCustomObject]@{fC=100;fF=50},
    [PSCustomObject]@{fC=110;fF=50},
    [PSCustomObject]@{fC=120;fF=40}
)

function Get-Poisson([double]$lambda) {
    $L = [Math]::Exp(-$lambda)
    $k = 0
    $p = 1.0
    do {
        $k++
        $u = [double]([System.Random]::new().NextDouble())
        $p *= $u
    } while ($p -gt $L)
    return ($k - 1)
}

$rng = [System.Random]::new()

function Get-PoissonFast([double]$lambda, [System.Random]$r) {
    $L = [Math]::Exp(-$lambda)
    $k = 0
    $p = 1.0
    do {
        $k++
        $p *= $r.NextDouble()
    } while ($p -gt $L)
    return ($k - 1)
}

Write-Host "+-----+-----+-------+--------+--------+---------+--------+---------+"
Write-Host "| fC  | fF  | Ratio |  lCasa |  lFora |  VitCasa| Empate |  VitFora|"
Write-Host "+-----+-----+-------+--------+--------+---------+--------+---------+"

foreach ($c in $cenarios) {
    $fC = [double]$c.fC
    $fF = [double]$c.fF
    $tot = $fC + $fF
    $dom = ($fC - $fF) / $tot
    $lC = $MEDIA * ($fC / $tot) * (1.0 + [Math]::Max($dom, 0.0) * $MULT)
    $lF = $MEDIA * ($fF / $tot) * (1.0 + [Math]::Max(-$dom, 0.0) * $MULT)

    $vc = 0; $ve = 0; $vf = 0
    for ($i = 0; $i -lt $N; $i++) {
        $gc = Get-PoissonFast $lC $rng
        $gf = Get-PoissonFast $lF $rng
        if ($gc -gt $gf) { $vc++ }
        elseif ($gc -eq $gf) { $ve++ }
        else { $vf++ }
    }

    $ratio = ($fC / $fF).ToString("F2")
    $lCStr = $lC.ToString("F3")
    $lFStr = $lF.ToString("F3")
    $vcStr = ($vc * 100.0 / $N).ToString("F1") + "%"
    $veStr = ($ve * 100.0 / $N).ToString("F1") + "%"
    $vfStr = ($vf * 100.0 / $N).ToString("F1") + "%"

    Write-Host ("|{0,5}|{1,5}| {2,5} |  {3,5} |  {4,5} |   {5,5} |  {6,5} |    {7,5}|" -f $c.fC, $c.fF, $ratio, $lCStr, $lFStr, $vcStr, $veStr, $vfStr)
}
Write-Host "+-----+-----+-------+--------+--------+---------+--------+---------+"
