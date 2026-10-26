import { COUNTRIES, Country } from './countries';
import { MESSAGES } from './messages';

// ---- config (mirrors application.yml of the Spring Boot backend) ----
const WEATHER_URL = 'https://api.open-meteo.com/v1/forecast';
const GEOCODING_URL = 'https://geocoding-api.open-meteo.com/v1/search';
const WIKIDATA_URL = 'https://query.wikidata.org/sparql';
const UPSTREAM_TIMEOUT_MS = 4000;
const HIGHLIGHTS_TIMEOUT_MS = 3000;
const CACHE_TTL_MS = 10 * 60 * 1000;
const SUPPORTED_LANGUAGES = ['en', 'fr', 'es', 'nl', 'de', 'it', 'ru', 'am'];
const DEFAULT_LANGUAGE = 'en';
const NATIVE_NAMES: Record<string, string> = {
  en: 'English', fr: 'Français', es: 'Español', nl: 'Nederlands',
  de: 'Deutsch', it: 'Italiano', ru: 'Русский', am: 'አማርኛ'
};

const CURRENT_PARAMS =
  'current=temperature_2m,relative_humidity_2m,apparent_temperature,' +
  'is_day,precipitation,weather_code,wind_speed_10m,wind_direction_10m';
const DAILY_PARAMS =
  'daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max';

export class HttpError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

// ---- tiny TTL cache (per warm function instance; the CDN adds a shared layer) ----
const cache = new Map<string, { expires: number; value: unknown }>();

async function cached<T>(key: string, load: () => Promise<T>, ttlOf?: (v: T) => number): Promise<T> {
  const hit = cache.get(key);
  if (hit && hit.expires > Date.now()) return hit.value as T;
  const value = await load();
  cache.set(key, { expires: Date.now() + (ttlOf ? ttlOf(value) : CACHE_TTL_MS), value });
  return value;
}

// ---- countries / languages ----
const sortedCountries = [...COUNTRIES].sort((a, b) => a.name.localeCompare(b.name));
const byCode = new Map(sortedCountries.map(c => [c.code.toUpperCase(), c]));

export function getCountries(continent?: string): Country[] {
  if (continent && continent.trim()) {
    return sortedCountries.filter(c => c.continent.toLowerCase() === continent.toLowerCase());
  }
  return sortedCountries;
}

export function findCountry(code: string): Country | undefined {
  return byCode.get(code.toUpperCase());
}

export function requireCountry(code: string): Country {
  const country = findCountry(code);
  if (!country) throw new HttpError(404, `Unknown country code: ${code}`);
  return country;
}

export function resolveLang(requested?: string | null): string {
  const normalized = (requested ?? '').toLowerCase().trim();
  return SUPPORTED_LANGUAGES.includes(normalized) ? normalized : DEFAULT_LANGUAGE;
}

export function languageList() {
  return SUPPORTED_LANGUAGES.map(code => ({ code, name: NATIVE_NAMES[code] ?? code }));
}

// ---- helpers ----
async function fetchJson(url: string, timeoutMs: number, init?: RequestInit): Promise<any> {
  try {
    const res = await fetch(url, { ...init, signal: AbortSignal.timeout(timeoutMs) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch {
    throw new HttpError(502, 'Could not reach the weather provider');
  }
}

const CODE_KEYS: Record<number, string> = {
  0: 'clear', 1: 'mainly_clear', 2: 'partly_cloudy', 3: 'overcast',
  45: 'fog', 48: 'rime_fog',
  51: 'drizzle_light', 53: 'drizzle_moderate', 55: 'drizzle_dense',
  56: 'freezing_drizzle_light', 57: 'freezing_drizzle_dense',
  61: 'rain_slight', 63: 'rain_moderate', 65: 'rain_heavy',
  66: 'freezing_rain_light', 67: 'freezing_rain_heavy',
  71: 'snow_slight', 73: 'snow_moderate', 75: 'snow_heavy', 77: 'snow_grains',
  80: 'rain_showers_slight', 81: 'rain_showers_moderate', 82: 'rain_showers_violent',
  85: 'snow_showers_slight', 86: 'snow_showers_heavy',
  95: 'thunderstorm', 96: 'thunderstorm_hail_slight', 99: 'thunderstorm_hail_heavy'
};

function conditionFor(code: number, lang: string) {
  const conditionKey = CODE_KEYS[code] ?? 'unknown';
  const key = `weather.${conditionKey}`;
  const condition = MESSAGES[lang]?.[key] ?? MESSAGES[DEFAULT_LANGUAGE][key] ?? key;
  return { conditionKey, condition };
}

const num = (v: unknown) => (typeof v === 'number' ? v : 0);

// ---- weather ----
async function buildCurrent(latitude: number, longitude: number, countryName: string,
                            countryCode: string, placeName: string, lang: string) {
  const url = `${WEATHER_URL}?latitude=${latitude}&longitude=${longitude}&${CURRENT_PARAMS}&timezone=auto`;
  const body = await fetchJson(url, UPSTREAM_TIMEOUT_MS);
  const current = body?.current;
  if (!current) throw new HttpError(502, `No weather data returned for ${placeName}`);
  return toWeather(current, latitude, longitude, countryName, countryCode, placeName, lang);
}

function toWeather(current: any, latitude: number, longitude: number, countryName: string,
                   countryCode: string, placeName: string, lang: string) {
  const weatherCode = num(current.weather_code);
  const { conditionKey, condition } = conditionFor(weatherCode, lang);
  return {
    countryName,
    countryCode,
    capital: placeName,
    latitude,
    longitude,
    temperature: num(current.temperature_2m),
    apparentTemperature: num(current.apparent_temperature),
    windSpeed: num(current.wind_speed_10m),
    windDirection: Math.trunc(num(current.wind_direction_10m)),
    humidity: Math.trunc(num(current.relative_humidity_2m)),
    precipitation: num(current.precipitation),
    isDay: num(current.is_day) === 1,
    weatherCode,
    conditionKey,
    condition,
    language: lang,
    time: String(current.time)
  };
}

export function getWeather(country: Country, lang: string) {
  return cached(`weather:${country.code}:${lang}`, () =>
    buildCurrent(country.latitude, country.longitude, country.name, country.code, country.capital, lang));
}

/**
 * Current weather for every country capital using ONE upstream request (Open-Meteo accepts
 * comma-separated coordinates). Lets the client warm its cache so any country opens instantly.
 */
export function getAllWeather(lang: string) {
  return cached(`all:${lang}`, async () => {
    const lat = sortedCountries.map(c => c.latitude).join(',');
    const lon = sortedCountries.map(c => c.longitude).join(',');
    const url = `${WEATHER_URL}?latitude=${lat}&longitude=${lon}&${CURRENT_PARAMS}&timezone=auto`;
    const body = await fetchJson(url, 8000);
    if (!Array.isArray(body) || body.length !== sortedCountries.length) {
      throw new HttpError(502, 'Unexpected response from the weather provider');
    }
    return sortedCountries.map((c, i) =>
      toWeather(body[i].current, c.latitude, c.longitude, c.name, c.code, c.capital, lang));
  });
}

export function getWeatherAt(lat: number, lon: number, placeName: string,
                             countryName: string, countryCode: string, lang: string) {
  return cached(`geo:${lat}:${lon}:${lang}`, () =>
    buildCurrent(lat, lon, countryName, countryCode, placeName, lang));
}

export function getForecast(country: Country, days: number, lang: string) {
  return cached(`forecast:${country.code}:${days}:${lang}`, async () => {
    const url = `${WEATHER_URL}?latitude=${country.latitude}&longitude=${country.longitude}` +
      `&${DAILY_PARAMS}&forecast_days=${days}&timezone=auto`;
    const body = await fetchJson(url, UPSTREAM_TIMEOUT_MS);
    const daily = body?.daily;
    if (!daily) throw new HttpError(502, `No forecast data returned for ${country.name}`);

    const dates: string[] = daily.time ?? [];
    return {
      countryName: country.name,
      countryCode: country.code,
      capital: country.capital,
      latitude: country.latitude,
      longitude: country.longitude,
      language: lang,
      days: dates.map((date, i) => {
        const weatherCode = num(daily.weather_code?.[i]);
        const { conditionKey, condition } = conditionFor(weatherCode, lang);
        return {
          date,
          temperatureMax: num(daily.temperature_2m_max?.[i]),
          temperatureMin: num(daily.temperature_2m_min?.[i]),
          precipitation: num(daily.precipitation_sum?.[i]),
          windSpeedMax: num(daily.wind_speed_10m_max?.[i]),
          weatherCode,
          conditionKey,
          condition
        };
      })
    };
  });
}

// ---- geocoding ----
export function geocode(query: string) {
  return cached(`geocode:${query.trim().toLowerCase()}`, async () => {
    const url = `${GEOCODING_URL}?name=${encodeURIComponent(query.trim())}&count=1&format=json`;
    const body = await fetchJson(url, UPSTREAM_TIMEOUT_MS);
    const first = body?.results?.[0];
    if (!first) return null;
    return {
      name: String(first.name),
      country: first.country == null ? '' : String(first.country),
      countryCode: first.country_code == null ? '' : String(first.country_code),
      latitude: Number(first.latitude),
      longitude: Number(first.longitude)
    };
  });
}

// ---- highlights (Wikidata) ----
function highlightsQuery(countryCode: string, limit: number): string {
  const safe = countryCode.replace(/[\\"]/g, '').toUpperCase();
  return `SELECT ?item ?itemLabel ?itemDescription ?typeLabel ?image ?article ?sitelinks WHERE {
  ?country wdt:P297 "${safe}".
  VALUES ?type {
    wd:Q570116 wd:Q41176 wd:Q11303 wd:Q4989906 wd:Q839954 wd:Q8502
    wd:Q34038 wd:Q23397 wd:Q4022 wd:Q46169 wd:Q188524 wd:Q23442
  }
  ?item wdt:P17 ?country;
        wdt:P31/wdt:P279* ?type;
        wikibase:sitelinks ?sitelinks.
  FILTER(?sitelinks >= 5)
  FILTER NOT EXISTS { ?item wdt:P31 wd:Q5 }
  OPTIONAL { ?item wdt:P18 ?image. }
  OPTIONAL {
    ?article schema:about ?item;
             schema:isPartOf <https://en.wikipedia.org/>.
  }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
}
ORDER BY DESC(?sitelinks)
LIMIT ${Math.max(1, Math.min(limit * 3, 30))}`;
}

export function getHighlights(country: Country) {
  return cached(`highlights:${country.code}`, async () => {
    const response = {
      countryName: country.name,
      countryCode: country.code,
      source: 'Wikidata',
      retrievedAt: new Date().toISOString(),
      live: true,
      highlights: [] as unknown[]
    };
    try {
      const limit = 8;
      const body = await fetchJson(WIKIDATA_URL, HIGHLIGHTS_TIMEOUT_MS, {
        method: 'POST',
        headers: {
          Accept: 'application/sparql-results+json',
          'Content-Type': 'application/x-www-form-urlencoded',
          'User-Agent': 'weather-app/1.0 (https://vercel.com)'
        },
        body: new URLSearchParams({ query: highlightsQuery(country.code, limit), format: 'json' })
      });
      const bindings: any[] = body?.results?.bindings ?? [];
      const val = (b: any, k: string): string | null => b?.[k]?.value ?? null;
      const byId = new Map<string, unknown>();
      for (const b of bindings) {
        const itemUrl = val(b, 'item');
        const wikidataId = itemUrl ? itemUrl.slice(itemUrl.lastIndexOf('/') + 1) : null;
        if (wikidataId && !byId.has(wikidataId)) {
          byId.set(wikidataId, {
            title: val(b, 'itemLabel'),
            category: val(b, 'typeLabel') || 'highlight',
            description: val(b, 'itemDescription'),
            imageUrl: val(b, 'image'),
            sourceUrl: val(b, 'article') || itemUrl,
            wikidataId
          });
        }
        if (byId.size >= limit) break;
      }
      response.highlights = [...byId.values()];
    } catch {
      // Same behavior as the Java service: fall back to an empty list.
    }
    return response;
  }, r => (r.highlights.length ? CACHE_TTL_MS : 60 * 1000)); // retry failed lookups sooner
}
