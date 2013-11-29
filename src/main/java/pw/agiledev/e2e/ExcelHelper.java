package pw.agiledev.e2e;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import pw.agiledev.e2e.annotation.ExcelEntity;
import pw.agiledev.e2e.annotation.ExcelProperty;
import pw.agiledev.e2e.exception.ExcelParseException;
import pw.agiledev.e2e.extension.ExcelType;

/**
 * 功能说明： EXCEL操作助手 
 * 操作说明：
 * 	使用方法很简单，只需要使用静态方法readExcel即可。
 * 		<code>
 * 			ExcelHelper eh = ExcelHelper.readExcel("excel文件名");
 * 		</code>
 * 	如果要读取Excel中的标题栏有哪些
 * 		<code>eh.getHeaders()</code>
 * 	如果要读取Excel中的数区域
 * 		<code>eh.getDatas()</code>
 * 	读取到的数据按照Excel中存放的行列形式存放在二维数组中
 * 	如果需要转换为实体列表的话
 * 		<code>eh.toEntitys(实体.class)</code>
 * 	注意到是，实体类必须含有@ExcelEntity注解，同时需要用到的属性字段上需要
 * 用@ExcelProperty标注。
 * 
 * @author 管宜尧 
 * 2013-11-28 上午10:20:53
 */
public class ExcelHelper {
	final public static int MIN_ROW_COLUMN_COUNT = 4;
	private int lastColumnIndex = 4;
	private String [] headers  = null;
	private String [][] datas = null;

	private ExcelHelper(){}
	
	@SuppressWarnings("rawtypes")
	private static List<Class<? extends ExcelType>> 
		userDefinedType = new ArrayList<Class<? extends ExcelType>>();
	
	/**
	 * 功能说明： 注册新字段类型
	 * 参数说明：
	 * 	扩展字段类型必须ExcelType抽象数据类型
	 * @author 管宜尧
	 * 2013-11-28 下午9:06:41
	 * @throws ExcelParseException 
	 */
	public static void registerNewType(@SuppressWarnings("rawtypes") Class<? extends ExcelType> type) 
			throws ExcelParseException{
		if(!userDefinedType.contains(type)){
			userDefinedType.add(type);
		}
	}
	
	/**
	 * 功能说明： 读取Excel内容
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午4:18:32
	 */
	public static ExcelHelper readExcel(String excelFilename) throws InvalidFormatException, IOException{
		return readExcel(excelFilename, 0);
	}
	/**
	 * 功能说明： 读取Excel内容
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午3:40:05
	 */
	public static ExcelHelper readExcel(String excelFilename, int sheetIndex) throws InvalidFormatException, IOException{
		return readExcel(new File(excelFilename), sheetIndex);
	}
	/**
	 * 功能说明： 读取Excel内容
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午3:40:05
	 */
	public static ExcelHelper readExcel(File file) throws InvalidFormatException, IOException{
		return readExcel(file, 0);
	}
	/**
	 * 功能说明： 读取Excel内容
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午3:40:05
	 */
	public static ExcelHelper readExcel(File file, int sheetIndex) throws InvalidFormatException, IOException{
		ExcelHelper eh = new ExcelHelper();
		// 读取Excel工作薄
		Workbook wb = WorkbookFactory.create(file);
		// 遍历Excel Sheet， 依次读取里面的内容
		if(sheetIndex > wb.getNumberOfSheets()){
			return null;
		}
		Sheet sheet = wb.getSheetAt(sheetIndex);
		// 遍历表格的每一行
		int rowStart = sheet.getFirstRowNum();
		// 最小行数为1行
		int rowEnd = sheet.getLastRowNum();
		// 读取EXCEL标题栏
		eh._parseExcelHeader(sheet.getRow(0));
		// 读取EXCEL数据区域内容
		eh._parseExcelData(sheet, rowStart + 1, rowEnd );
		return eh;
	}
	/**
	 * 功能说明： 获取表格数据二维表
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午4:25:23
	 */
	public String [][] getDatas(){
		return datas;
	}
	/**
	 * 功能说明： 获取表格标题
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午4:25:41
	 */
	public String [] getHeaders(){
		return headers;
	}
	/**
	 * 功能说明：转换为实体 
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午4:34:48
	 * @throws ExcelParseException 
	 */
	public <T> List<T>  toEntitys(Class<T> classType) throws ExcelParseException{
		// 如果实体没有@ExcelEntity，则不允许继续操作
		ExcelEntity excelEntity = classType.getAnnotation(ExcelEntity.class);
		if(excelEntity == null){
			throw new ExcelParseException("转换的实体必须存在@ExcelEntity!");
		}
		// 创建Excel实体字段信息
		List<ExcelEntityField> eefs = _getEntityFields(classType);
		
		// 创建实体对象集
		List<T> entitys = new ArrayList<T>();
		try {
			// 遍历提交的数据行，依次填充到创建的实体对象中
			for(String [] data : datas){
				T obj = classType.newInstance();
				// 遍历实体对象的实体字段，通过反射为实体字段赋值
				for(ExcelEntityField eef : eefs){
					Method method = obj.getClass().getDeclaredMethod("set" + 
							_toCapitalizeCamelCase(eef.getField().getName()), 
							eef.getField().getType());
					try{
						System.out.println(_getFieldValue(data[eef.getIndex()], eef.getField()));
						method.invoke(obj, _getFieldValue(data[eef.getIndex()], eef.getField()));
					}catch(Exception e){
						if(eef.isRequired()){
							throw new ExcelParseException("字段" + eef.getFieldName() + "出错!", e);
						}
						continue;
					}
				}
				entitys.add(obj);
			}
		} catch (Exception e) {
			throw new ExcelParseException(e);
		}
		
		return entitys;
	}
	/**
	 * 功能说明： 转换驼峰命名方式
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午8:03:35
	 */
	private String _toCapitalizeCamelCase(String name) {
		if (name == null) {
            return null;
        }
		//name = name.toLowerCase();

        StringBuilder sb = new StringBuilder(name.length());
        boolean upperCase = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            if (c == '_') {
                upperCase = true;
            } else if (upperCase) {
                sb.append(Character.toUpperCase(c));
                upperCase = false;
            } else {
                sb.append(c);
            }
        }
        name = sb.toString();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
	}
	/**
	 * 功能说明： 获取Excel实体类中的填充字段
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午7:55:05
	 */
	private <T> List<ExcelEntityField> _getEntityFields(Class<T> classType) throws ExcelParseException {
		List<ExcelEntityField> eefs = new ArrayList<ExcelHelper.ExcelEntityField>();
		// 遍历所有字段
		Field[] allFields = classType.getDeclaredFields();
		for(Field field: allFields){
			ExcelProperty excelProperty = field.getAnnotation(ExcelProperty.class);
			// 只对含有@ExcelProperty注解的字段进行赋值
			if(excelProperty == null){
				continue;
			}
			ExcelEntityField eef = new ExcelEntityField();
			
			String key = excelProperty.value().trim();// Excel Header名
			boolean required = excelProperty.required(); // 该列是否为必须列
			
			int index = _indexOfHeader(key);
			// 如果字段必须，而索引为-1 ，说明没有这一列，抛错
			if(required && index == -1){
				throw new ExcelParseException("字段" + key + "必须!");
			}
			
			eef.setField(field);
			eef.setFieldName(key);
			eef.setRequired(required);
			eef.setIndex(_indexOfHeader(key));
			
			eefs.add(eef);
		}
		return eefs;
	}
	/**
	 * 功能说明： 获取字段的值，路由不同的字段类型
	 * 参数说明：
	 * 	value   值
	 * 	field 需要填充的字段
	 * @author 管宜尧
	 * 2013-11-28 下午7:55:35
	 */
	@SuppressWarnings("rawtypes")
	private Object _getFieldValue(String value, Field field) throws ExcelParseException {
		Class<?> type = field.getType();
		String typeName = type.getName();
		// 字符串
		if("java.lang.String".equals(typeName)){
			return value;
		}
		// 长整形
		if("java.lang.Long".equals(typeName) || "long".equals(typeName)){
			return Long.parseLong(value);
		}
		// 整形
		if("java.lang.Integer".equals(typeName) || "int".equals(typeName)){
			return Integer.parseInt(value);
		}
		// 短整型
		if("java.lang.Short".equals(typeName) || "short".equals(typeName)){
			return Short.parseShort(value);
		}
		// Date型
		if("java.util.Date".equals(typeName)){
			try {
				return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value);
			} catch (ParseException e) {
				throw new ExcelParseException("日期类型格式有误!");
			}
		}
		// Timestamp
		if("java.sql.Timestamp".equals(typeName)){
			try {
				return new Timestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value).getTime());
			} catch (ParseException e) {
				throw new ExcelParseException("时间戳类型格式有误!");
			}
		}
		// Char型
		if("java.lang.Character".equals(typeName) || "char".equals(typeName)){
			if(value.length() == 1){
				return value.charAt(0);
			}
		}
		
		for(Class<? extends ExcelType> et : userDefinedType){
			if(et.getName().equals(typeName)){
				try {
					ExcelType newInstance = et.newInstance();
					return newInstance.parseValue(value);
				} catch (InstantiationException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
		
		throw new ExcelParseException("不支持的字段类型 " + typeName + " !" );
	}
	/**
	 * 功能说明： 列名在列标题中的索引
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午7:57:01
	 */
	private int _indexOfHeader(String columnName){
		for(int i = 0; i < headers.length; i ++){
			if(headers[i].equals(columnName)){
				return i;
			}
		}
		return -1;
	}
	
	/**
	 * 功能说明： 解析EXCEL标题栏
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午3:47:38
	 */
	private void _parseExcelHeader(Row row) {
		lastColumnIndex = Math.max(row.getLastCellNum(),MIN_ROW_COLUMN_COUNT);
		headers = new String [lastColumnIndex];
		// 初始化headers，每一列的标题
		for(int columnIndex = 0; columnIndex < lastColumnIndex; columnIndex ++){
			Cell cell = row.getCell(columnIndex, Row.RETURN_BLANK_AS_NULL);
			headers[columnIndex] = _getCellValue(cell).trim();
		}
	}
	/**
	 * 功能说明： 解析EXCEL数据区域内容
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午3:47:27
	 */
	private void _parseExcelData(Sheet sheet, int rowStart, int rowEnd) {
		datas = new String [rowEnd][lastColumnIndex];
		for(int rowIndex = rowStart; rowIndex <= rowEnd; rowIndex ++	){
			Row row  = sheet.getRow(rowIndex);
			int rowNumber = rowIndex - rowStart;
			// 读取遍历每一行中的每一列
			for(int columnIndex = 0;columnIndex < lastColumnIndex; columnIndex ++){
				Cell cell = row.getCell(columnIndex, Row.RETURN_BLANK_AS_NULL);
				String value = _getCellValue(cell).trim();
				datas[rowNumber][columnIndex] = value;
			}
		}
	}
	/**
	 * 功能说明： 读取每个单元格中的内容
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午3:27:43
	 */
	private String _getCellValue(Cell cell) {
		// 如果单元格为空的，则返回空字符串
		if(cell == null){
			return "";
		}
		
		// 根据单元格类型，以不同的方式读取单元格的值
		String value = "";
		switch (cell.getCellType()) {
			case Cell.CELL_TYPE_STRING:
				value = cell.getRichStringCellValue().getString();
				break;
			case Cell.CELL_TYPE_NUMERIC:
				if (DateUtil.isCellDateFormatted(cell)){
					value = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cell.getDateCellValue());
				}else{
					value = (long)cell.getNumericCellValue() + "";
				}
				break;
			case Cell.CELL_TYPE_BOOLEAN:
				value = cell.getBooleanCellValue() ? "TRUE" : "FALSE";
				break;
			case Cell.CELL_TYPE_FORMULA:
				value = cell.getCellFormula();
				break;
			default:
		}
		return value;
	}
	
	/**
	 * 功能说明： Excel实体字段类（内部类）
	 * 参数说明：
	 * @author 管宜尧
	 * 2013-11-28 下午7:56:37
	 */
	private class ExcelEntityField{
		private String fieldName;
		private boolean required;
		private Field field;
		private int index;
		
		public int getIndex() {
			return index;
		}
		public void setIndex(int index) {
			this.index = index;
		}
		public String getFieldName() {
			return fieldName;
		}
		public void setFieldName(String fieldName) {
			this.fieldName = fieldName;
		}
		public boolean isRequired() {
			return required;
		}
		public void setRequired(boolean required) {
			this.required = required;
		}
		public Field getField() {
			return field;
		}
		public void setField(Field field) {
			this.field = field;
		}
	}
	
}
