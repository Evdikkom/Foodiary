Write-Host "[Foodiary] Creating virtual environment..."
python -m venv .venv

Write-Host "[Foodiary] Activating virtual environment..."
.\.venv\Scripts\Activate.ps1

Write-Host "[Foodiary] Installing dependencies..."
pip install --upgrade pip
pip install -r requirements.txt

if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
    Write-Host "[Foodiary] .env created from .env.example. Fill in LogMeal tokens before first real request."
} else {
    Write-Host "[Foodiary] .env already exists."
}

Write-Host "[Foodiary] Setup complete."
