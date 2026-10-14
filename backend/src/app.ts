import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import { env } from './config/env';
import { errorHandler, notFoundHandler } from './common/middleware/errorHandler';
import routes from './routes';

const app = express();

app.disable('x-powered-by');
app.use(helmet());
app.use(cors({ origin: env.CORS_ORIGIN.split(',').map((s) => s.trim()), credentials: true }));
app.use(express.json({ limit: '1mb' }));

app.use('/api/v1', routes);

app.use(notFoundHandler);
app.use(errorHandler); // phải đứng cuối cùng

export default app;
