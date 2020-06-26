package springboard.model;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementSetter;

import model.JdbcTemplateConst;

/*
 	JdbcTemplate 관련 주요메소드
 	
 	- List query(String sql, RowMapper rowMapper)
 		: 여러개의 레코드를 반환하는 select계열의 쿼리문인 경우 사용한다.
 		
 	- List query(String sql, Object[] args, RowMapper rowMapper)
 		: 인파라미터를 가진 여러개의 레코드를 반환하는 select계열의 쿼리문인 경우 사용한다.
 		
 	- int queryForInt(String sql) 혹은 queryForInt(String sql, Object['] args)
 		: 쿼리문의 실행결과가 숫자를 반환하는 select계열의 쿼리문에 사용한다.
 		
 	- Object queryForObject(String sql, RowMapper rowMapper)
 		혹은 Object queryForObject(String sql, Object[] args, RowMapper rowMapper)
 		: 하나의 레코드를 반환하는 select계열의 쿼리문 실행시 사용된다.
 		
 	- int update(String sql)
 		: 인파라미터가 없는 update/delete/insert 쿼리문을 처리할 때 사용함
 		
 	- int update(String sql, Object[] args)
 		: 인파라미터가 있는 update/delete/insert 쿼리문을 처리할 때 사용함.
 */

public class JDBCTemplateDAO {

	// 멤버변수
	JdbcTemplate template;
	
	public JDBCTemplateDAO() {
		/*
		 	컨트롤러에서 @Autowired를 통해 자동주입 받았던 빈을
		 	정적변수인 JdbcTemplateConst.template 을 통해 가져온다.
		 	즉, DB연결정보를 웹어플리케이션 어디서든 사용할 수 있다.
		 */
		this.template = JdbcTemplateConst.template;
		System.out.println("JDBCTemplateDAO() 생성자 호출");
	}
	public void close() {
		//JDTCTemplate에서는 사용하지 않음.
	}
	public int getTotalCount(Map<String, Object> map) {
		String sql = "SELECT COUNT(*) FROM springboard ";
				
		if(map.get("Word")!=null) {
			sql += " WHERE " +map.get("Column")+ " "
				+ "		LIKE '%"+map.get("Word")+"%'	";	
		}
		
		return template.queryForObject(sql, Integer.class);
	}
	
	// 게시판 리스트 (페이지처리x)
	public ArrayList<SpringBbsDTO> list(Map<String, Object> map) {
		
		String sql = ""	+" SELECT * FROM springboard ";
			if(map.get("Word")!=null) {
				sql +=" WHERE "+map.get("Column")+" "
					+ " LIKE '%"+map.get("Word")+"%' ";
			}
			// sql += " ORDER BY idx DESC "; //-> 답변글 사용하지 않을 경우
			sql += " ORDER BY bgroup DESC, bstep ASC "; ///-> 답변글 적용시...
		
		/*
		 	query메소드의 반환타입은 List계열의 컬렉션이므로 제네릭부분만
		 	우리가 필요한 DTO객체로 대체하면 된다. 나머지는 RowMapper객체가
		 	모두 알아서 처리해준다.
		 */
		return (ArrayList<SpringBbsDTO>)
			template.query(sql,	new BeanPropertyRowMapper<SpringBbsDTO>(SpringBbsDTO.class));
	}
	
	// 글쓰기처리
	public void write(final SpringBbsDTO springBbsDTO) {
		template.update(new PreparedStatementCreator() {
			@Override
			public PreparedStatement
				createPreparedStatement(Connection con) throws SQLException{
				
				/*
				 	답변형 게시판에서 원본글인 경우에는 idx(일련번호)와 bgroup(그룹번호)가
				 	반드시 일치해야 한다.
				 	또한 nextVal은 한문장에서 여러번 사용하더라도 같은 시퀀스를 반환한다.
				 */
				String sql = "INSERT INTO springboard ("
					+ " idx, name, title, contents, hits "
					+ " ,bgroup, bstep, bindent, pass) "
					+ " VALUES ("
					+ " springboard_seq.NEXTVAL,?,?,?,0,"
					+ " springboard_seq.NEXTVAL,0,0,?)";
				
				PreparedStatement psmt = con.prepareStatement(sql);
				psmt.setString(1, springBbsDTO.getName());
				psmt.setString(2, springBbsDTO.getTitle());
				psmt.setString(3, springBbsDTO.getContents());
				psmt.setString(4, springBbsDTO.getPass());
				
				return psmt;
				}	
		});
	}
		
	// 조회수 증가
	public void updateHit(final String idx) {
		String sql= "update springboard set hits=hits+1 where idx=?";
		
		/*
		  	매개변수로 전달되는 idx를 아래 익명클래스내부에서 사용하기 위해서는
		  	반드시 final로 선언해야 사용이 가능하다.
		  	(자바의 규칙)
		 */
		template.update(sql,new PreparedStatementSetter() {
         
			@Override
			public void setValues(PreparedStatement ps) throws SQLException {
				ps.setInt(1, Integer.parseInt(idx));
			}
		});
	}
	
	// 상세보기 처리
	public SpringBbsDTO view(String idx) {
	  
		updateHit(idx);
      
		SpringBbsDTO dto = new SpringBbsDTO();
		String sql = "select * from springboard where idx="+idx;
      
		/*
			queryForObject()메소드는 반환결과가 0개인거나 2개이상인 경우 예외가 발생하므로
			반드시 예외처리를 해주는것이 좋다.
		*/
		try {
			dto = template.queryForObject(sql, new BeanPropertyRowMapper<SpringBbsDTO>(SpringBbsDTO.class));
		} 
		catch (Exception e) {
			System.out.println("view() 실행시 예외 발생");
		}
		
		return dto;
	}
	
	public int password(String idx, String pass) {
		int retNum = 0;
		String sql = "SELECT * FROM springboard WHERE pass=" + pass + " AND idx="+idx;
		
		/*
		  	만약 패스워드가 틀린경우라면 반환되는 행이 0개이므로
		  	예외처리를 하고 있다. queryForObject()는 반환되는 행이
		  	1개일때만 정상 동작한다.
		 */
		try {
			SpringBbsDTO dto = template.queryForObject(sql, 
					new BeanPropertyRowMapper<SpringBbsDTO>(SpringBbsDTO.class));
			retNum = dto.getIdx();
		}
		catch (Exception e) {
			System.out.println("password() 예외발생");
		}
		
		return retNum;
	}
	
	// 수정처리
	public void edit(final SpringBbsDTO dto) {
		/*
		 	해당 게시판에서 패스워드는 변경의 대상이 아니라
		 	검증의 대상으로만 사용됨.
		 	따라서 set절이 아니라 where절에 삽입된다.
		 */
		String sql = "UPDATE springboard SET name=?, title=?, contents=? WHERE idx=? AND pass=?";
		
		/*
	        매개변수 dto객체를 아래 익명클래스 내부에서 사용해야 하므로
	        반드시 final을 붙여줘야 한다. 
	     */
		template.update(sql, new PreparedStatementSetter() { 
			@Override
			public void setValues(PreparedStatement ps) throws SQLException {
				ps.setString(1, dto.getName());
				ps.setString(2, dto.getTitle());
				ps.setString(3, dto.getContents());
				ps.setInt(4, dto.getIdx());
				ps.setString(5, dto.getPass());
			}
		});
	}
	
	//삭제처리
	public void delete(final String idx, final String pass) {
		
		String sql = "DELETE FROM springboard " + " WHERE idx=? AND pass=? ";
		
		template.update(sql, new PreparedStatementSetter () {
			@Override
			public void setValues(PreparedStatement ps)
				throws SQLException {
				ps.setString(1, idx);
				ps.setString(2, pass);
			}
		});
	}
	
	// 답변글 쓰기
	public void reply(final SpringBbsDTO dto) {
		
		// 답변글쓰기전 레코드 업데이트
		replyPrevUpdate(dto.getBgroup(), dto.getBstep());
		
		/*
		 	원본글의 경우 idx와 bgroup은 동일한 값을 입력함.
		 	답변글의 경우 원본글의 group번호를 그대로 가져와서 입력함.
		 	idx은 시퀀스를 통해 bgroup은 원본글과 동일하게 입력.
		 */
		String sql = "INSERT INTO springboard "
			+ " (idx, name, title, contents, pass, "
			+ " bgroup, bstep, bindent) "
			+ " VALUES "
			+ " (springboard_seq.nextval, ?, ?, ?, ?, "
			+ " ?, ?, ?)";

		/*
		 	답변글인 경우 원본글의 step+1, indent+1 처리하여 입력한다.
		 */
		template.update(sql, new PreparedStatementSetter() {
			
			@Override
			public void setValues(PreparedStatement ps) throws SQLException {
				ps.setString(1, dto.getName());
				ps.setString(2, dto.getTitle());
				ps.setString(3, dto.getContents());
				ps.setString(4, dto.getPass());
				ps.setInt(5, dto.getBgroup());
				ps.setInt(6, dto.getBstep()+1);
				ps.setInt(7, dto.getBindent()+1);
			}
		});
	}
	
	// 답변글 입력전 레코드 일괄 업데이트(step을 뒤로 밀어주기 위한 로직)
	public void replyPrevUpdate(final int strGroup, final int strStep) {
		
		String sql = "update springboard "
				+ " set bstep = bstep + 1 "
				+ " where bgroup=? and bstep>?";
		template.update(sql, new PreparedStatementSetter() {
			
			@Override
			public void setValues(PreparedStatement ps) throws SQLException {
				ps.setInt(1, strGroup);
				ps.setInt(2, strStep);
			}
		});
	}
	
	// 리스트처리(페이지o)
	public ArrayList<SpringBbsDTO> listPage(Map<String, Object> map){

		int start = Integer.parseInt(map.get("start").toString());
		int end = Integer.parseInt(map.get("end").toString());
		
		String sql = ""
				+"SELECT * FROM ("
				+"    SELECT Tb.*, rownum rNum FROM ("
				+"        SELECT * FROM springboard ";				
			if(map.get("Word")!=null){
				sql +=" WHERE "+map.get("Column")+" "
					+ " LIKE '%"+map.get("Word")+"%' ";				
			}			
			sql += " ORDER BY bgroup DESC, bstep ASC"
			+"    ) Tb"
			+")"
			+" WHERE rNum BETWEEN "+start+" and "+end;
		
		return (ArrayList<SpringBbsDTO>)
			template.query(sql, 
				new BeanPropertyRowMapper<SpringBbsDTO>(
				SpringBbsDTO.class));
	}


}