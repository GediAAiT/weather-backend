import type { IncomingMessage, ServerResponse } from 'node:http';
import {
  HttpError, findCountry, getAllWeather, geocode, getCountries, getForecast, getHighlights,
  getWeather, getWeatherAt, languageList, requireCountry, resolveLang
} from './_lib/service';

/**
 * Single serverless entry point replacing the Spring Boot controllers.
 * Routes: /api/weather[/overview|/batch|/city|/coords|/forecast],
 *         /api/countries[/{code}/highlights], /api/meta/{languages|health}
 */
export default async function handler(req: IncomingMessage, res: ServerResponse): Promise<void> {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');
  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.end();
    return;
  }

  try {
    const url = new URL(req.url ?? '/', 'http://localhost');
    const q = url.searchParams;
    // vercel.json rewrites /api/:path* to this function with the path in ?__path=
    const rewritten = q.get('__path');
    q.delete('__path');
    const rawPath = rewritten ?? url.pathname.replace(/^\/api\/?/, '');
    const segments = rawPath.split('/').filter(Boolean);
    const route = segments.join('/');

    const required = (name: string): string => {
      const v = q.get(name);
      if (v === null || v === '') throw new HttpError(400, `Required parameter '${name}' is not present.`);
      return v;
    };
    const number = (name: string): number => {
      const n = Number(required(name));
      if (Number.isNaN(n)) throw new HttpError(400, `Parameter '${name}' must be a number.`);
      return n;
    };
    const lang = () => resolveLang(q.get('lang'));

    let body: unknown;
    let maxAge = 600; // seconds the Vercel CDN may cache the response

    if (route === 'weather') {
      body = await getWeather(requireCountry(required('code')), lang());
    } else if (route === 'weather/all') {
      body = await getAllWeather(lang());
    } else if (route === 'weather/overview') {
      const country = requireCountry(required('code'));
      const l = lang();
      // Weather is the priority: highlights get at most 1s, then fall back to an empty list
      // (the frontend shows its built-in highlights) so a slow Wikidata never delays the page.
      const slowHighlights = new Promise<{ highlights: unknown[] }>(resolve =>
        setTimeout(() => resolve({ highlights: [] }), 1000));
      const [weather, highlights] = await Promise.all([
        getWeather(country, l),
        Promise.race([getHighlights(country), slowHighlights])
      ]);
      body = { weather, highlights };
    } else if (route === 'weather/batch') {
      const l = lang();
      const countries = required('codes').split(',')
        .map(c => c.trim()).filter(Boolean)
        .map(findCountry).filter((c): c is NonNullable<typeof c> => !!c);
      body = await Promise.all(countries.map(c => getWeather(c, l)));
    } else if (route === 'weather/city') {
      const query = required('q');
      const l = lang();
      const place = await geocode(query);
      if (!place) throw new HttpError(404, `Unknown place: ${query}`);
      body = await getWeatherAt(place.latitude, place.longitude, place.name, place.country, place.countryCode, l);
    } else if (route === 'weather/coords') {
      const lat = number('lat');
      const lon = number('lon');
      if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
        throw new HttpError(400, 'Coordinates out of range: lat must be -90..90, lon must be -180..180');
      }
      body = await getWeatherAt(lat, lon, `${lat},${lon}`, '', '', lang());
    } else if (route === 'weather/forecast') {
      const code = required('code');
      const days = q.get('days') === null ? 7 : number('days');
      if (!Number.isInteger(days) || days < 1 || days > 16) {
        throw new HttpError(400, 'days must be between 1 and 16');
      }
      body = await getForecast(requireCountry(code), days, lang());
    } else if (route === 'countries') {
      body = getCountries(q.get('continent') ?? undefined);
      maxAge = 86400;
    } else if (segments.length === 3 && segments[0] === 'countries' && segments[2] === 'highlights') {
      body = await getHighlights(requireCountry(segments[1]));
    } else if (route === 'meta/languages') {
      body = languageList();
      maxAge = 86400;
    } else if (route === 'meta/health') {
      body = { status: 'ok' };
      maxAge = 0;
    } else {
      throw new HttpError(404, `No such endpoint: /api/${route}`);
    }

    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Cache-Control', maxAge > 0
      ? `public, s-maxage=${maxAge}, stale-while-revalidate=60`
      : 'no-store');
    res.end(JSON.stringify(body));
  } catch (err) {
    const status = err instanceof HttpError ? err.status : 500;
    const message = err instanceof Error && err.message ? err.message : 'Unexpected error';
    res.statusCode = status;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('Cache-Control', 'no-store');
    res.end(JSON.stringify({
      timestamp: new Date().toISOString(),
      status,
      error: status === 404 ? 'Not Found' : status === 400 ? 'Bad Request'
        : status === 502 ? 'Bad Gateway' : 'Internal Server Error',
      message
    }));
  }
}
