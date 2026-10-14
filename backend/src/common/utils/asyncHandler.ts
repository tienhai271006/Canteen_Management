import type { NextFunction, Request, RequestHandler, Response } from 'express';

/** Bọc handler async để mọi lỗi tự chuyển vào errorHandler (Express 4 không tự làm). */
export const asyncHandler =
  (fn: (req: Request, res: Response, next: NextFunction) => Promise<unknown>): RequestHandler =>
  (req, res, next) => {
    fn(req, res, next).catch(next);
  };
