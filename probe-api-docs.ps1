param(
  [int]$Port = 8091
)

$ErrorActionPreference = 'Stop'

$uri = "http://localhost:$Port/api/v1/api-docs"
try {
  $resp = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 30
  "STATUS=$($resp.StatusCode)" | Out-File -FilePath 'probe-result.txt' -Encoding utf8
  $resp.Content | Out-File -FilePath 'probe-body.txt' -Encoding utf8
} catch {
  "ERROR=$($_.Exception.GetType().FullName)" | Out-File -FilePath 'probe-result.txt' -Encoding utf8
  $errorText = $_ | Out-String
  $errorText | Out-File -FilePath 'probe-error.txt' -Encoding utf8

  $response = $_.Exception.Response
  if ($response -ne $null) {
    $stream = $response.GetResponseStream()
    if ($stream -ne $null) {
      $reader = New-Object System.IO.StreamReader($stream)
      $body = $reader.ReadToEnd()
      $body | Out-File -FilePath 'probe-body.txt' -Encoding utf8
    }
  }
}
